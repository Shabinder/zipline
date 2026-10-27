package app.cash.zipline

import app.cash.zipline.testing.newEndpointPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay

internal class MixedServiceReturnTest {
  interface First : ZiplineService { fun value(): String }
  interface Second : ZiplineService { fun number(): Int }
  interface Factory : ZiplineService {
    suspend fun first(): First
    suspend fun second(): Second
    suspend fun nullableFirst(): First?
    suspend fun nullableSecond(): Second?
  }

  private fun check(reverse: Boolean) = runBlocking {
    val (host, guest) = newEndpointPair(this)
    val first = object : First { override fun value() = "first" }
    val second = object : Second { override fun number() = 42 }
    host.bind<Factory>("factory", object : Factory {
      override suspend fun first(): First { delay(1); return first }
      override suspend fun second(): Second { delay(1); return second }
      override suspend fun nullableFirst(): First? { delay(1); return first }
      override suspend fun nullableSecond(): Second? { delay(1); return second }
    })
    val factory = guest.take<Factory>("factory")
    repeat(2) {
      if (reverse) {
        assertEquals(42, factory.second().number())
        assertEquals("first", factory.first().value())
        assertEquals(42, factory.nullableSecond()?.number())
        assertEquals("first", factory.nullableFirst()?.value())
      } else {
        assertEquals("first", factory.first().value())
        assertEquals(42, factory.second().number())
        assertEquals("first", factory.nullableFirst()?.value())
        assertEquals(42, factory.nullableSecond()?.number())
      }
    }
    factory.close()
  }

  @Test fun firstThenSecond() = check(false)
  @Test fun secondThenFirst() = check(true)
}

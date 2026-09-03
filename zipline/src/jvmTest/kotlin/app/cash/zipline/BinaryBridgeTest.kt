package app.cash.zipline

import app.cash.zipline.testing.BinaryEchoService
import app.cash.zipline.testing.BinarySink
import app.cash.zipline.testing.ContextualBinaryEchoService
import app.cash.zipline.testing.loadTestingJs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * Correctness for binary payloads crossing the bridge as bytes rather than as text.
 *
 * Speed is [bench.BinaryBridgeBenchmark]'s job. This one asks whether the bytes are the same bytes
 * in every shape a real caller can produce: empty, huge, several at once, absent, nested in a
 * returned value, a view into a larger buffer, across a suspending call and a flow, and with a
 * reentrant call nested inside another call that also carries buffers.
 */
class BinaryBridgeTest {
  private val dispatcher = StandardTestDispatcher()
  private val zipline = Zipline.create(dispatcher)

  private suspend fun service(): BinaryEchoService {
    zipline.loadTestingJs()
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareBinaryEchoService()")
    return zipline.take("binaryEchoService")
  }

  @Test fun emptyAndTiny(): Unit = runTest(dispatcher) {
    val service = service()
    assertEquals(0, service.sinkBytes(ByteArray(0)))
    assertContentEquals(ByteArray(0), service.echoBytes(ByteArray(0)))
    assertContentEquals(byteArrayOf(7), service.echoBytes(byteArrayOf(7)))
    // Every byte value, so a signed/unsigned confusion in the copy shows up.
    val allBytes = ByteArray(256) { it.toByte() }
    assertContentEquals(allBytes, service.echoBytes(allBytes))
    zipline.close()
  }

  @Test fun oneMegabyte(): Unit = runTest(dispatcher) {
    val service = service()
    val payload = Random(1).nextBytes(1024 * 1024)
    assertContentEquals(payload, service.echoBytes(payload))
    zipline.close()
  }

  @Test fun severalBuffersInOneCall(): Unit = runTest(dispatcher) {
    val service = service()
    val a = Random(2).nextBytes(1000)
    val b = Random(3).nextBytes(2000)
    val c = Random(4).nextBytes(3000)
    assertContentEquals(a + b + c, service.concat(a, b, c))
    zipline.close()
  }

  @Test fun nullablePayload(): Unit = runTest(dispatcher) {
    val service = service()
    assertEquals(-1, service.sinkNullable(null))
    assertEquals(5, service.sinkNullable(ByteArray(5)))
    // An absent payload must not consume an index, so a following call still lines up.
    assertEquals(-1, service.sinkNullable(null))
    assertEquals(9, service.sinkNullable(ByteArray(9)))
    zipline.close()
  }

  @Test fun bytesNestedInAResult(): Unit = runTest(dispatcher) {
    val service = service()
    val payload = Random(5).nextBytes(4096)
    val envelope = service.wrap(payload)
    assertEquals("wrapped", envelope.label)
    assertContentEquals(payload, envelope.payload)
    zipline.close()
  }

  @Test fun viewIntoALargerBuffer(): Unit = runTest(dispatcher) {
    val service = service()
    val payload = ByteArray(1000) { it.toByte() }
    val slice = service.slice(payload, 100, 50)
    assertContentEquals(payload.copyOfRange(100, 150), slice)
    zipline.close()
  }

  @Test fun suspendingCall(): Unit = runTest(dispatcher) {
    val service = service()
    val payload = Random(6).nextBytes(64 * 1024)
    assertContentEquals(payload, service.echoSuspending(payload))
    zipline.close()
  }

  @Test fun flowOfPayloads(): Unit = runTest(dispatcher) {
    val service = service()
    val payload = Random(7).nextBytes(1024)
    val received = service.stream(payload, 5).toList()
    assertEquals(5, received.size)
    received.forEachIndexed { index, bytes ->
      assertContentEquals(payload + index.toByte(), bytes)
    }
    zipline.close()
  }

  @Test fun failureLeavesTheBridgeUsable(): Unit = runTest(dispatcher) {
    val service = service()
    val payload = Random(8).nextBytes(2048)
    assertFailsWith<Exception> { service.boom(payload) }
    // The buffers from the failed call must not linger and shift the next call's indices.
    assertContentEquals(payload, service.echoBytes(payload))
    zipline.close()
  }

  @Test fun reentrantCallNestsBuffers(): Unit = runTest(dispatcher) {
    val service = service()
    val payload = Random(9).nextBytes(3000)

    val hostService = object : BinarySink {
      override fun sink(payload: ByteArray) = payload.size
    }

    val result = service.reentrant(payload, hostService)
    assertContentEquals(payload + payload.size.toByte(), result)
    zipline.close()
  }

  @Test fun contextualAnnotationResolvesTheSameWay(): Unit = runTest(dispatcher) {
    zipline.loadTestingJs()
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareBinaryEchoService()")
    val contextual = zipline.take<ContextualBinaryEchoService>("contextualBinaryEchoService")
    val payload = Random(10).nextBytes(8192)
    assertEquals(payload.size, contextual.sink(payload))
    assertContentEquals(payload, contextual.echo(payload))
    zipline.close()
  }

  @Test fun payloadIsNotInTheJson(): Unit = runTest(dispatcher) {
    // The whole point: the JSON carries an index, not the bytes. A recognisable payload must not
    // appear in the encoded call at all.
    val calls = mutableListOf<String>()
    val listening = Zipline.create(
      dispatcher,
      eventListener = object : EventListener() {
        override fun callStart(zipline: Zipline, call: Call): Any? {
          calls += call.encodedCall
          return null
        }
      },
    )
    listening.loadTestingJs()
    listening.quickJs.evaluate("testing.app.cash.zipline.testing.prepareBinaryEchoService()")
    val service = listening.take<BinaryEchoService>("binaryEchoService")

    val payload = ByteArray(64) { 0x7B } // '{' repeated: obvious in JSON if it leaked.
    service.sinkBytes(payload)

    // Encoded calls name functions by signature hash, not by name, so check them all rather than
    // looking for one: none may carry the payload, and none may be payload-sized.
    assertTrue(calls.isNotEmpty(), "the event listener saw no calls")
    val leaked = calls.filter { "{{{{" in it }
    assertTrue(leaked.isEmpty(), "the payload leaked into the JSON: $leaked")
    val longest = calls.maxBy { it.length }
    assertTrue(longest.length < 300, "an encoded call is payload-sized (${longest.length}): $longest")
    listening.close()
  }
}

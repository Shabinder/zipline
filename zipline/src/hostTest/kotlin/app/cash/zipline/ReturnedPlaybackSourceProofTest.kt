/*
 * Copyright (C) 2026 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.zipline

import app.cash.zipline.testing.ReturnedPlaybackSource
import app.cash.zipline.testing.ReturnedPlaybackSourceFactory
import app.cash.zipline.testing.ReturnedPlaybackSourceMonitor
import app.cash.zipline.testing.loadTestingJs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class ReturnedPlaybackSourceProofTest {
  private val dispatcher = StandardTestDispatcher()

  @Test fun guestReturnedSourceReadsSeeksAndCloses(): Unit = runTest(dispatcher) {
    val encodedCalls = mutableListOf<String>()
    val encodedBinaryResults = mutableListOf<String>()
    val leakedServices = mutableListOf<String>()
    val returnedSourceServiceNames = mutableListOf<String>()
    val zipline = Zipline.create(
      dispatcher,
      eventListener = object : EventListener() {
        override fun callStart(zipline: Zipline, call: Call): Any? {
          encodedCalls += call.encodedCall
          return null
        }

        override fun callEnd(zipline: Zipline, call: Call, result: CallResult, startValue: Any?) {
          if (result.result.getOrNull() is ByteArray) {
            encodedBinaryResults += result.encodedResult
          }
          if (result.result.getOrNull() is ReturnedPlaybackSource) {
            returnedSourceServiceNames += result.serviceNames
          }
        }

        override fun serviceLeaked(zipline: Zipline, name: String) {
          leakedServices += name
        }
      },
    )
    try {
      zipline.loadTestingJs()
      zipline.quickJs.evaluate(
        "testing.app.cash.zipline.testing.prepareReturnedPlaybackSourceJsBridges()",
      )

      val monitor = zipline.take<ReturnedPlaybackSourceMonitor>("returnedPlaybackSourceMonitor")
      val scope = ZiplineScope()
      val factory = zipline.take<ReturnedPlaybackSourceFactory>(
        "returnedPlaybackSourceFactory",
        scope,
      )

      val seed = 17
      val source = factory.open(seed)
      assertEquals(1, monitor.activeSourceCount())
      assertEquals(0, monitor.closedSourceCount())
      assertEquals("audio/mp4", source.descriptor().mimeType)

      for (size in listOf(0, 1, 64 * 1024, 256 * 1024, 1024 * 1024)) {
        val offset = 1234L
        val expected = ByteArray(size) { index -> expectedByte(seed, offset, index) }
        assertContentEquals(expected, source.read(offset, size), "size=$size")
      }

      source.seek(123_456L)
      assertEquals(123_456L, source.descriptor().lastSeekPositionMs)

      source.failNextRead()
      assertFailsWith<Exception> { source.read(0L, 1024) }
      assertContentEquals(
        ByteArray(1024) { index -> expectedByte(seed, 0L, index) },
        source.read(0L, 1024),
      )

      source.close()
      source.close()
      assertEquals(0, monitor.activeSourceCount())
      assertEquals(1, monitor.closedSourceCount())
      assertFailsWith<IllegalStateException> { source.read(0L, 1) }

      val scopeOwnedSource = factory.open(seed + 1)
      assertEquals(1, monitor.activeSourceCount())
      scope.close()
      assertEquals(0, monitor.activeSourceCount())
      assertEquals(2, monitor.closedSourceCount())
      assertFailsWith<IllegalStateException> { scopeOwnedSource.read(0L, 1) }

      assertTrue(encodedCalls.maxOf { it.length } < 512, "a call contained binary payload bytes")
      assertEquals(6, encodedBinaryResults.size, "every successful read must be observed")
      assertTrue(
        encodedBinaryResults.maxOf { it.length } < 512,
        "a successful byte result contained binary payload bytes",
      )
      assertEquals(2, returnedSourceServiceNames.size, "both opened sources must cross by reference")
      assertTrue(
        leakedServices.none { it in returnedSourceServiceNames },
        "a returned playback source leaked: returned=$returnedSourceServiceNames leaked=$leakedServices",
      )

      monitor.close()
    } finally {
      zipline.close()
    }
  }

  private fun expectedByte(seed: Int, offset: Long, index: Int): Byte = ((seed + (offset % 95L).toInt() + index) % 95 + 32).toByte()
}

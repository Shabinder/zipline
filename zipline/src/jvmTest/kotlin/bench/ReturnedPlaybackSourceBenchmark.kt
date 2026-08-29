package bench

import app.cash.zipline.Zipline
import app.cash.zipline.testing.ReturnedPlaybackSource
import app.cash.zipline.testing.ReturnedPlaybackSourceFactory
import app.cash.zipline.testing.loadTestingJs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/** Measures binary reads on a guest-created service returned to the host by reference. */
class ReturnedPlaybackSourceBenchmark {
  private val dispatcher = StandardTestDispatcher()

  @Test fun returnedSourceReads(): Unit = runTest(dispatcher, timeout = 30.minutes) {
    val zipline = Zipline.create(dispatcher)
    try {
      zipline.loadTestingJs()
      zipline.quickJs.evaluate(
        "testing.app.cash.zipline.testing.prepareReturnedPlaybackSourceJsBridges()",
      )
      val factory = zipline.take<ReturnedPlaybackSourceFactory>("returnedPlaybackSourceFactory")

      val openStart = System.nanoTime()
      val source = factory.open(SEED)
      val openMicros = (System.nanoTime() - openStart) / 1_000.0
      println("PLAYBACK_SOURCE|open=${openMicros}us descriptor=${source.descriptor()}")

      for (size in SIZES) {
        val binary = measure(size, BINARY_WARMUPS, BINARY_RUNS) {
          source.read(OFFSET, size).size
        }
        // The text lane is intentionally sampled less: its payload-sized JSON dominates runtime.
        val text = measure(size, TEXT_WARMUPS, TEXT_RUNS) {
          source.readText(OFFSET, size).length
        }
        println("PLAYBACK_SOURCE|${size.label()} binary ${binary.report()}")
        println("PLAYBACK_SOURCE|${size.label()} text   ${text.report()}")
      }

      source.close()
      factory.close()
    } finally {
      zipline.close()
    }
  }

  private suspend inline fun measure(
    size: Int,
    warmups: Int,
    runs: Int,
    crossinline block: suspend () -> Int,
  ): LongArray {
    repeat(warmups) { assertEquals(size, block()) }
    return LongArray(runs) {
      val start = System.nanoTime()
      assertEquals(size, block())
      System.nanoTime() - start
    }
  }

  private fun LongArray.report(): String {
    val sorted = sorted()
    return "median=${sorted[size / 2] / 1_000.0}us " +
      "p90=${sorted[size * 9 / 10] / 1_000.0}us min=${sorted.first() / 1_000.0}us"
  }

  private fun Int.label() = "${this / 1024}KB"

  private companion object {
    const val SEED = 19
    const val OFFSET = 4096L
    const val BINARY_WARMUPS = 500
    const val BINARY_RUNS = 2_000
    const val TEXT_WARMUPS = 3
    const val TEXT_RUNS = 10
    val SIZES = listOf(64 * 1024, 256 * 1024)
  }
}

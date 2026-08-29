package bench

import app.cash.zipline.Zipline
import app.cash.zipline.testing.BinaryEchoService
import app.cash.zipline.testing.loadTestingJs
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * What a payload costs as text against what it costs as bytes, alternating in one process.
 *
 * Separate runs were not good enough: on this machine the same lane varied by 50% between runs,
 * which is wider than the effects being compared. Alternating inside one JVM puts both lanes
 * through the same JIT state, the same heap and the same engine.
 *
 * The gap is the point. A string argument must become a JavaScript string and then a Kotlin/JS
 * one; a `ByteArray` never becomes text at all. Measured here: 1.3x at 1.5 KB, 12x at 64 KB, 48x
 * at 1 MB - and a per-call floor around 190 us that neither lane escapes.
 *
 * An intermediate was tried and rejected: handing the engine UTF-8 for `JS_ParseJSON`, so no
 * JavaScript string is built for the call and `JSON.parse` never re-scans one. It measured
 * 1.1-1.3x, a wash at 16 KB, and cost the guest's EventListener its view of the encoded call,
 * since there is no longer a string to report. Not worth two code paths.
 */
class CallPathBenchmark {
  private val dispatcher = StandardTestDispatcher()
  private val zipline = Zipline.create(dispatcher)

  @Test fun callPaths(): Unit = runTest(dispatcher, timeout = 30.minutes) {
    zipline.loadTestingJs()
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareBinaryEchoService()")
    val service = zipline.take<BinaryEchoService>("binaryEchoService")

    for (size in SIZES) {
      val text = CharArray(size) { ('a' + (it % 26)) }.concatToString()
      val bytes = Random(size).nextBytes(size)

      // Alternate, so drift in the machine hits both lanes equally.
      val byteList = bytes.toList()

      // The old lane gets far fewer iterations, because it is far slower: at 64 KB it costs over
      // 100 ms a call, so matching the fast lanes' count would take minutes per size.
      val stringPath = LongArray(RUNS)
      val binary = LongArray(RUNS)
      val jsonArray = LongArray(SLOW_RUNS)
      repeat(WARMUPS) {
        service.sinkString(text)
        service.sinkBytes(bytes)
      }
      repeat(SLOW_WARMUPS) { service.sinkByteList(byteList) }

      for (i in 0 until RUNS) {
        var start = System.nanoTime()
        assertEquals(size, service.sinkString(text))
        stringPath[i] = System.nanoTime() - start

        start = System.nanoTime()
        assertEquals(size, service.sinkBytes(bytes))
        binary[i] = System.nanoTime() - start
      }
      for (i in 0 until SLOW_RUNS) {
        val start = System.nanoTime()
        assertEquals(size, service.sinkByteList(byteList))
        jsonArray[i] = System.nanoTime() - start
      }

      println("CALLPATH|${size.label()} json array of numbers (old): ${jsonArray.report()}")
      println("CALLPATH|${size.label()} string:                      ${stringPath.report()}")
      println("CALLPATH|${size.label()} bytes  (new):                 ${binary.report()}")
      println(
        "CALLPATH|${size.label()} bytes vs old ${"%.1f".format(jsonArray.median() / binary.median())}x, " +
          "vs string ${"%.1f".format(stringPath.median() / binary.median())}x",
      )
    }

    zipline.close()
  }

  private fun LongArray.median(): Double = sorted()[size / 2].toDouble()

  private fun LongArray.report(): String {
    val s = sorted()
    return "median ${s[size / 2] / 1000.0}us, min ${s.first() / 1000.0}us, " +
      "p90 ${s[size * 9 / 10] / 1000.0}us"
  }

  private fun Int.label() = when {
    this >= 1024 * 1024 -> "${this / (1024 * 1024)}MB"
    this % 1024 == 0 -> "${this / 1024}KB"
    else -> "${this / 1024.0}KB"
  }.padEnd(6)

  private companion object {
    /**
     * Enough iterations for the JIT to settle.
     *
     * An earlier version used 5 warmups and 31 runs and produced nonsense: a 64 KB call finishing
     * faster than an empty one, and a "190 us floor" that turned out to be warmup. Properly warmed,
     * an empty call is 26 us. Anything below a few hundred iterations here measures the JIT.
     */
    const val WARMUPS = 500
    const val RUNS = 2_000
    const val SLOW_WARMUPS = 3
    const val SLOW_RUNS = 15
    val SIZES = listOf(1536, 16 * 1024, 64 * 1024, 256 * 1024)
  }
}

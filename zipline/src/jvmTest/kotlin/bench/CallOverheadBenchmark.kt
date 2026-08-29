package bench

import app.cash.zipline.QuickJs
import app.cash.zipline.Zipline
import app.cash.zipline.testing.BinaryEchoService
import app.cash.zipline.testing.loadTestingJs
import kotlin.test.Test
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * Where does a call's fixed cost actually go?
 *
 * The payload benchmarks kept producing incoherent orderings - a 64 KB call finishing faster than
 * an empty one - which is the signature of a fixed cost that dwarfs the payload. An in-process call
 * has no business costing 190 us: a JNI transition is well under a microsecond and a QuickJS invoke
 * is a few. So this peels the layers apart instead of measuring them together.
 *
 * Each layer adds exactly one thing over the one above it.
 */
class CallOverheadBenchmark {

  @Test fun layers() {
    val quickJs = QuickJs.create()
    quickJs.evaluate(
      """
      globalThis.app_cash_zipline_inboundChannel = {
        call: function(json, buffers) { return '{}'; },
        takeResultBuffers: function() { return []; },
        disconnect: function() { return true; }
      };
      globalThis.f = function() { return 1; };
      """.trimIndent(),
    )

    // 1. The engine alone: parse and run a trivial expression.
    val evaluate = measure { quickJs.evaluate("1") }

    // 2. The engine alone, calling a function that already exists.
    val jsCall = measure { quickJs.evaluate("f()") }

    // 3. JNI plus a JavaScript invoke plus two string conversions, and no Zipline codec at all.
    val channel = quickJs.getInboundChannel()
    val rawChannel = measure { channel.call("{}", emptyArray()) }

    println("OVERHEAD|engine evaluate('1'):      ${evaluate.report()}")
    println("OVERHEAD|engine evaluate('f()'):    ${jsCall.report()}")
    println("OVERHEAD|raw channel call:          ${rawChannel.report()}")
    quickJs.close()
  }

  @Test fun ziplineCall(): Unit = runTest(StandardTestDispatcher()) {
    val zipline = Zipline.create(StandardTestDispatcher())
    zipline.loadTestingJs()
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareBinaryEchoService()")
    val service = zipline.take<BinaryEchoService>("binaryEchoService")

    // 4. The whole stack: encode the call, cross, decode it in the guest, dispatch, encode the
    //    result, cross back, decode it.
    val full = measure { service.ping() }
    println("OVERHEAD|zipline service call:      ${full.report()}")

    // 5. The same, with one small string argument, to price a trivial payload against the floor.
    val withArg = measure { service.sinkString("hello") }
    println("OVERHEAD|zipline call, 5-char arg:  ${withArg.report()}")

    zipline.close()
  }

  private inline fun measure(block: () -> Unit): LongArray {
    repeat(WARMUPS) { block() }
    return LongArray(RUNS) {
      val start = System.nanoTime()
      block()
      System.nanoTime() - start
    }
  }

  private fun LongArray.report(): String {
    val sorted = sorted()
    return "median ${sorted[size / 2] / 1000.0}us, min ${sorted.first() / 1000.0}us, " +
      "p90 ${sorted[size * 9 / 10] / 1000.0}us"
  }

  private companion object {
    const val WARMUPS = 2_000
    const val RUNS = 10_000
  }
}

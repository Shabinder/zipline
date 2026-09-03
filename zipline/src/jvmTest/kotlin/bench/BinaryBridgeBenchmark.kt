package bench

import app.cash.zipline.Zipline
import app.cash.zipline.testing.BinaryEchoService
import app.cash.zipline.testing.loadTestingJs
import java.util.Base64
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.json.Json

/**
 * Prices a binary payload crossing Zipline's bridge, end to end.
 *
 * `Docs/RESEARCH/zipline-binary-bridge.md` measured the JS-side decode only and named the round
 * trip as the measurement to repeat. This is that: a real service call, so the number includes the
 * host-side encode, Zipline's JSON envelope, the guest's parse, and the same again on the way back.
 *
 * Two lanes: a `ByteArray` parameter, which now travels beside the JSON as bytes, and a base64
 * `String`, which is what the same payload costs as text and is kept as the before-picture. `ping`
 * prices the envelope with no payload, so payload cost can be separated from call cost.
 */
class BinaryBridgeBenchmark {
  private val dispatcher = StandardTestDispatcher()
  private val zipline = Zipline.create(dispatcher)

  @Test fun binaryRoundTrip(): Unit = runTest(dispatcher, timeout = 15.minutes) {
    zipline.loadTestingJs()
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareBinaryEchoService()")
    val service = zipline.take<BinaryEchoService>("binaryEchoService")

    val ping = measure { service.ping() }
    println("BENCH|ping (envelope only): ${ping.report()}")

    for (size in SIZES) {
      val bytes = Random(size).nextBytes(size)
      val base64 = Base64.getEncoder().encodeToString(bytes)

      val sinkBytes = measure { assertEquals(size, service.sinkBytes(bytes)) }
      val echoBytes = measure { assertEquals(size, service.echoBytes(bytes).size) }
      // The host-side encode and decode are inside the timing: they are part of the cost.
      // What an ordinary call costs today: the payload is text, and so is the whole call.
      val text = CharArray(size) { ('a' + (it % 26)) }.concatToString()
      val textBytes = text.encodeToByteArray()
      val sinkString = measure { assertEquals(size, service.sinkString(text)) }
      val sinkTextBytes = measure { assertEquals(size, service.sinkTextAsBytes(textBytes)) }

      val sinkB64 = measure {
        assertEquals(size, service.sinkBase64(Base64.getEncoder().encodeToString(bytes)))
      }
      val echoB64 = measure {
        assertEquals(size, Base64.getDecoder().decode(service.echoBase64(base64)).size)
      }
      // Attribution: the host's share of the ByteArray lane, before anything reaches the engine.
      val hostJson = measure { Json.encodeToString(ByteArraySerializer(), bytes) }
      val hostB64 = measure { Base64.getEncoder().encodeToString(bytes) }
      // What the same bytes cost to copy on the JVM alone, as a floor for any memcpy bridge.
      val memcpy = measure { bytes.copyOf() }

      println("BENCH|${size.label()} sinkBytes:  ${sinkBytes.report()}")
      println("BENCH|${size.label()} echoBytes:  ${echoBytes.report()}")
      println("BENCH|${size.label()} sinkString: ${sinkString.report()}")
      println("BENCH|${size.label()} sinkTxtByt: ${sinkTextBytes.report()}")
      println("BENCH|${size.label()} sinkBase64: ${sinkB64.report()}")
      println("BENCH|${size.label()} echoBase64: ${echoB64.report()}")
      println("BENCH|${size.label()} hostJson:   ${hostJson.report()}")
      println("BENCH|${size.label()} hostBase64: ${hostB64.report()}")
      println("BENCH|${size.label()} jvmMemcpy:  ${memcpy.report()}")
    }

    zipline.close()
  }

  /** Runs [block] [WARMUPS] then [RUNS] times, returning the run durations in microseconds. */
  private inline fun measure(block: () -> Unit): LongArray {
    repeat(WARMUPS) { block() }
    return LongArray(RUNS) {
      val start = System.nanoTime()
      block()
      (System.nanoTime() - start) / 1_000
    }
  }

  private fun LongArray.report(): String {
    val sorted = sorted()
    return "median ${sorted[size / 2]}us, min ${sorted.first()}us, max ${sorted.last()}us"
  }

  private fun Int.label() = when {
    this >= 1024 * 1024 -> "${this / (1024 * 1024)}MB"
    this % 1024 == 0 -> "${this / 1024}KB"
    else -> "${this / 1024.0}KB"
  }.padEnd(6)

  private companion object {
    const val WARMUPS = 3
    const val RUNS = 11
    val SIZES = listOf(1536, 64 * 1024, 256 * 1024, 1024 * 1024)
  }
}

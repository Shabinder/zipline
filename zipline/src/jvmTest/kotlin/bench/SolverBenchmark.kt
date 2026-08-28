package bench

import app.cash.zipline.QuickJs
import java.io.File
import kotlin.test.Test

/**
 * G8 of the QuickJS upgrade: run the real yt-dlp-ejs whole-player rewrite inside *Zipline's*
 * QuickJS rather than the qjs CLI, so the 132,596 ms baseline and the new figure are measured in
 * the same embedding. Skips rather than fails when the inputs are absent.
 */
class SolverBenchmark {
  private fun quote(s: String): String {
    val sb = StringBuilder(s.length + 64)
    sb.append('"')
    for (c in s) {
      when (c) {
        '\\' -> sb.append("\\\\")
        '"' -> sb.append("\\\"")
        '\n' -> sb.append("\\n")
        '\r' -> sb.append("\\r")
        '\t' -> sb.append("\\t")
        else -> if (c < ' ' || c == '\u2028' || c == '\u2029') {
          sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
        } else sb.append(c)
      }
    }
    return sb.append('"').toString()
  }

  @Test fun wholePlayerRewrite() {
    val assets = File("/Users/admin/SoundBound/soundbound-extensions/extensions-src/youtube/src/commonMain/resources/ytjs")
    val player = File("/Users/admin/Tools/po-quickjs/base.js")
    if (!assets.isDirectory || !player.isFile) { println("BENCH|skipped - inputs missing"); return }

    QuickJs.create().use { qjs ->
      val t0 = System.currentTimeMillis()
      qjs.evaluate(File(assets, "quickjs-polyfills.js").readText(), "quickjs-polyfills.js")
      qjs.evaluate(File(assets, "yt.solver.lib.min.js").readText(), "yt.solver.lib.min.js")
      qjs.evaluate("var meriyah = lib.meriyah, astring = lib.astring;", "glue.js")
      qjs.evaluate(File(assets, "yt.solver.core.min.js").readText(), "yt.solver.core.min.js")
      println("BENCH|solver load: ${System.currentTimeMillis() - t0}ms")

      val src = player.readText()
      val tInj = System.currentTimeMillis()
      qjs.evaluate("globalThis.__player = " + quote(src) + ";", "player.js")
      println("BENCH|player injected (${src.length} chars): ${System.currentTimeMillis() - tInj}ms")

      // The whole-player rewrite does NOT work inside Zipline's QuickJS - it throws
      // "unconsistent stack size" on BOTH the 2021 and 2026 engines (pre-existing, not a
      // regression). Recorded rather than asserted: this exists to measure, and the failure
      // itself is the measurement. On 2021 it grinds for ~156s before throwing; on 2026 it
      // throws in about a second.
      val t1 = System.currentTimeMillis()
      val out = try { qjs.evaluate(
        """
        var res = jsc({
          type: 'player', player: globalThis.__player, output_preprocessed: true,
          requests: [ { type: 'sig', challenges: ['abcdefghijklmnop'] },
                      { type: 'n', challenges: ['xyz123'] } ]
        });
        JSON.stringify({ sig: res.responses[0].data['abcdefghijklmnop'],
                         n: res.responses[1].data['xyz123'],
                         pre: (res.preprocessed_player || '').length })
        """.trimIndent(), "bench.js",
      ) } catch (e: Exception) { "THREW: " + e.message }
      println("BENCH|WHOLE-PLAYER REWRITE: ${System.currentTimeMillis() - t1}ms")
      println("BENCH|result: $out")
    }
  }
}

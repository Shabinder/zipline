package bench

import app.cash.zipline.QuickJs
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs yt-dlp-ejs's whole-player rewrite inside Zipline's QuickJS against a real YouTube player.
 *
 * This is the workload that motivated upgrading the engine, and it is the one that could not run
 * on the old one: QuickJS 2021-03-27 spent about 156 seconds on it and then threw
 * "unconsistent stack size: 2 3 (pc=2895)". On 2026-06-04 it completes in a few seconds and
 * returns the right answer, so it is asserted rather than merely measured.
 *
 * Skips when its inputs are absent, since they are fetched by hand rather than checked in.
 */
class SolverBenchmark {
  @Test fun wholePlayerRewrite() {
    val assets = File(SOLVER_ASSETS)
    val player = File(PLAYER)
    if (!assets.isDirectory || !player.isFile) {
      println("BENCH|skipped - solver assets or player missing")
      return
    }

    QuickJs.create().use { quickJs ->
      val loadStart = System.currentTimeMillis()
      quickJs.evaluate(File(assets, "quickjs-polyfills.js").readText(), "quickjs-polyfills.js")
      quickJs.evaluate(File(assets, "yt.solver.lib.min.js").readText(), "yt.solver.lib.min.js")
      // The browser page gets these as globals; the bundle exposes them on `lib`.
      quickJs.evaluate("var meriyah = lib.meriyah, astring = lib.astring;", "glue.js")
      quickJs.evaluate(File(assets, "yt.solver.core.min.js").readText(), "yt.solver.core.min.js")
      println("BENCH|solver load: ${System.currentTimeMillis() - loadStart}ms")

      val source = player.readText()
      quickJs.evaluate("globalThis.__player = ${source.toJsStringLiteral()};", "player.js")

      val solveStart = System.currentTimeMillis()
      val json = quickJs.evaluate(
        """
        var result = jsc({
          type: 'player', player: globalThis.__player, output_preprocessed: true,
          requests: [
            { type: 'sig', challenges: ['abcdefghijklmnop'] },
            { type: 'n', challenges: ['xyz123'] }
          ]
        });
        JSON.stringify({
          sig: result.responses[0].data['abcdefghijklmnop'],
          n: result.responses[1].data['xyz123'],
          preprocessedLength: (result.preprocessed_player || '').length
        })
        """.trimIndent(),
        "solve.js",
      ) as String
      println("BENCH|whole-player rewrite: ${System.currentTimeMillis() - solveStart}ms")
      println("BENCH|result: $json")

      // The signature transform is a reversal, so this is checkable without the player.
      assertTrue(""""sig":"ponmlkjihgfedcba"""" in json, "unexpected signature: $json")
      assertTrue(""""n":"""" in json, "no throttling parameter: $json")
      assertTrue(""""preprocessedLength":3""" in json, "no preprocessed player: $json")
    }
  }

  /** Renders [this] as a JavaScript string literal, so a 2.5 MB player can be handed to eval. */
  private fun String.toJsStringLiteral(): String {
    val result = StringBuilder(length + 64).append('"')
    for (c in this) {
      when {
        c == '\\' -> result.append("\\\\")
        c == '"' -> result.append("\\\"")
        c == '\n' -> result.append("\\n")
        c == '\r' -> result.append("\\r")
        c == '\t' -> result.append("\\t")
        c < ' ' || c == ' ' || c == ' ' ->
          result.append("\\u").append(c.code.toString(16).padStart(4, '0'))
        else -> result.append(c)
      }
    }
    return result.append('"').toString()
  }

  private companion object {
    const val SOLVER_ASSETS =
      "/Users/admin/SoundBound/soundbound-extensions/extensions-src/youtube/src/commonMain/resources/ytjs"
    const val PLAYER = "/Users/admin/Tools/po-quickjs/base.js"
  }
}

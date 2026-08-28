package app.cash.zipline

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Isolates the JNI conversions from the codec: a hand-written JavaScript channel reports what it
 * actually received, and hands back typed arrays for the host to read.
 */
class BufferConversionTest {
  private val quickJs = QuickJs.create()

  @Test fun hostToGuest() {
    quickJs.evaluate(
      """
      globalThis.app_cash_zipline_inboundChannel = {
        call: function(json, buffers) {
          var parts = [];
          for (var i = 0; i < buffers.length; i++) {
            var b = buffers[i];
            parts.push(b.constructor.name + ':' + b.length + ':' + Array.prototype.join.call(b, '-'));
          }
          return 'count=' + buffers.length + ' ' + parts.join(' ');
        },
        takeResultBuffers: function() { return []; },
        disconnect: function() { return true; }
      };
      """.trimIndent(),
    )

    val channel = quickJs.getInboundChannel()
    val result = channel.call("{}", arrayOf(byteArrayOf(1, 2, 3), ByteArray(0), byteArrayOf(-1)))
    println("CONVERT|$result")
    assertEquals("count=3 Int8Array:3:1-2-3 Int8Array:0: Int8Array:1:-1", result)
    quickJs.close()
  }

  @Test fun guestToHost() {
    quickJs.evaluate(
      """
      globalThis.app_cash_zipline_inboundChannel = {
        call: function(json, buffers) { return 'ok'; },
        takeResultBuffers: function() {
          var whole = new Int8Array([10, 20, 30, 40]);
          var view = whole.subarray(1, 3);
          return [whole, view, new Int8Array(0)];
        },
        disconnect: function() { return true; }
      };
      """.trimIndent(),
    )

    val channel = quickJs.getInboundChannel()
    channel.call("{}", emptyArray())
    val buffers = channel.takeResultBuffers()
    println("CONVERT|returned ${buffers.size}: ${buffers.map { it.toList() }}")
    assertEquals(3, buffers.size)
    assertContentEquals(byteArrayOf(10, 20, 30, 40), buffers[0])
    // The view must survive as its own 2 bytes, not as the whole backing buffer.
    assertContentEquals(byteArrayOf(20, 30), buffers[1])
    assertContentEquals(ByteArray(0), buffers[2])
    quickJs.close()
  }
}

package app.cash.zipline.internal.bridge

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.EmptySerializersModule

/** Reproduces buffer replacement during decoding without relying on GC timing. */
class CallCodecReentrancyTest {
  private fun codec(): CallCodec = Endpoint(
    CoroutineScope(Dispatchers.Unconfined),
    EmptySerializersModule(),
    Endpoint.EventListener(),
    object : CallChannel {
      override fun call(callJson: String, buffers: Array<ByteArray>): String = error("unused")
      override fun takeResultBuffers(): Array<ByteArray> = error("unused")
      override fun disconnect(instanceName: String): Boolean = error("unused")
    },
    { error("unused") },
  ).callCodec

  private fun bytes(codec: CallCodec, nested: () -> Unit = {}) = object : KSerializer<ByteArray> {
    override val descriptor = PrimitiveSerialDescriptor("buffer-index", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: ByteArray): Unit = error("unused")
    override fun deserialize(decoder: Decoder): ByteArray {
      val index = decoder.decodeInt()
      nested()
      return codec.buffer(index)
    }
  }

  @Test fun nestedResultKeepsOuterPayload() {
    val codec = codec()
    val outer = byteArrayOf(1, 2, 3)
    val inner = byteArrayOf(9, 8)
    codec.setDecodingBuffers(arrayOf(outer))
    val serializer = bytes(codec) {
      codec.setDecodingBuffers(arrayOf(inner))
      val result = codec.decodeResult(ResultOrCallbackSerializer(bytes(codec)), "{\"success\":0}")
      assertContentEquals(inner, result.result.getOrThrow() as ByteArray)
    }
    val result = codec.decodeResult(ResultOrCallbackSerializer(serializer), "{\"success\":0}")
    assertContentEquals(outer, result.result.getOrThrow() as ByteArray)
  }

  @Test fun failedNestedDecodeRestoresOuterAndNextMessage() {
    val codec = codec()
    val outer = byteArrayOf(4, 5, 6)
    codec.setDecodingBuffers(arrayOf(outer))
    val serializer = bytes(codec) {
      codec.setDecodingBuffers(emptyArray())
      assertFailsWith<IllegalStateException> {
        codec.decodeResultOrCallback(ResultOrCallbackSerializer(bytes(codec)), "{\"success\":0}")
      }
    }
    val result = codec.decodeResultOrCallback(ResultOrCallbackSerializer(serializer), "{\"success\":0}")
    assertContentEquals(outer, result.value.result!!.getOrThrow() as ByteArray)
    val next = byteArrayOf(7)
    codec.setDecodingBuffers(arrayOf(next))
    val nextResult = codec.decodeResult(ResultOrCallbackSerializer(bytes(codec)), "{\"success\":0}")
    assertContentEquals(next, nextResult.result.getOrThrow() as ByteArray)
  }
}

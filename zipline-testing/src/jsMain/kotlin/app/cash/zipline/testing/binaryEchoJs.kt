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
package app.cash.zipline.testing

import app.cash.zipline.Zipline
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.khronos.webgl.Int8Array

@OptIn(ExperimentalEncodingApi::class)
class JsBinaryEchoService : BinaryEchoService {
  override fun ping() = 0

  override fun sinkBytes(payload: ByteArray) = payload.size

  override fun echoBytes(payload: ByteArray) = payload

  override fun sinkBase64(payload: String) = Base64.decode(payload).size

  override fun echoBase64(payload: String) = Base64.encode(Base64.decode(payload))

  override fun concat(first: ByteArray, second: ByteArray, third: ByteArray) = first + second + third

  override fun sinkNullable(payload: ByteArray?) = payload?.size ?: -1

  override fun wrap(payload: ByteArray) = BinaryEnvelope("wrapped", payload)

  /**
   * Returns a **view** rather than a copy, which is what a real guest hands back: Kotlin/JS
   * `ByteArray` is an `Int8Array`, and `subarray` shares the underlying buffer at an offset. A host
   * that reads `.buffer` without honouring `byteOffset`/`byteLength` gets the wrong bytes here.
   */
  override fun slice(payload: ByteArray, offset: Int, length: Int): ByteArray {
    val source = payload.unsafeCast<Int8Array>()
    return source.subarray(offset, offset + length).unsafeCast<ByteArray>()
  }

  override suspend fun echoSuspending(payload: ByteArray) = payload

  override fun stream(payload: ByteArray, count: Int): Flow<ByteArray> = flow {
    repeat(count) { index ->
      emit(payload + index.toByte())
    }
  }

  override fun boom(payload: ByteArray): ByteArray = throw IllegalStateException("boom ${payload.size}")

  override fun reentrant(payload: ByteArray, service: BinarySink): ByteArray {
    // Calls back into the host while this call is still open, so the inner call's buffers have to
    // nest inside the outer call's without either losing its own.
    val innerSize = service.sink(payload)
    return payload + innerSize.toByte()
  }
}

class JsContextualBinaryEchoService : ContextualBinaryEchoService {
  override fun sink(payload: ByteArray) = payload.size

  override fun echo(payload: ByteArray) = payload
}

private val zipline by lazy { Zipline.get() }

@JsExport
fun prepareBinaryEchoService() {
  zipline.bind<BinaryEchoService>("binaryEchoService", JsBinaryEchoService())
  zipline.bind<ContextualBinaryEchoService>(
    "contextualBinaryEchoService",
    JsContextualBinaryEchoService(),
  )
}

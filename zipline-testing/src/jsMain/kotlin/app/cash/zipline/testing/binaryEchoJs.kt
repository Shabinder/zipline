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

@OptIn(ExperimentalEncodingApi::class)
class JsBinaryEchoService : BinaryEchoService {
  override fun ping() = 0

  override fun sinkBytes(payload: ByteArray) = payload.size

  override fun echoBytes(payload: ByteArray) = payload

  override fun sinkBase64(payload: String) = Base64.decode(payload).size

  override fun echoBase64(payload: String) = Base64.encode(Base64.decode(payload))
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

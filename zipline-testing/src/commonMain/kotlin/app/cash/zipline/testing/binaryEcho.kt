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

import app.cash.zipline.ZiplineService

/**
 * Carries binary payloads across the bridge, the two ways it can be done today.
 *
 * `bytes` is what a `ByteArray` parameter does out of the box: kotlinx.serialization renders it as
 * a JSON array of numbers. `base64` is the best a caller can do without changing Zipline's codec.
 * Both are here so a benchmark can price them against each other and against an empty call.
 */
interface BinaryEchoService : ZiplineService {
  /** Prices Zipline's envelope with no payload at all. */
  fun ping(): Int

  /** Host to guest only. Returns the size so the guest must have decoded it. */
  fun sinkBytes(payload: ByteArray): Int

  /** Host to guest and back. */
  fun echoBytes(payload: ByteArray): ByteArray

  /** Host to guest only, base64. The guest decodes to bytes, as a real consumer would. */
  fun sinkBase64(payload: String): Int

  /** Host to guest and back, base64, with a decode and re-encode in the guest. */
  fun echoBase64(payload: String): String
}

/**
 * The same payloads, but every `ByteArray` is `@Contextual`, so Zipline's compiler plugin routes
 * them to the serializer registered in the endpoint's `SerializersModule` instead of kotlinx's
 * built-in one.
 *
 * This is a separate interface on purpose: the plugin collects the types it needs serializers for
 * into a `Set<IrType>`, and a plain `ByteArray` and a `@Contextual ByteArray` in the same interface
 * are liable to collapse into one entry.
 */
interface ContextualBinaryEchoService : ZiplineService {
  fun sink(payload: @kotlinx.serialization.Contextual ByteArray): Int

  fun echo(
    payload: @kotlinx.serialization.Contextual ByteArray,
  ): @kotlinx.serialization.Contextual ByteArray
}

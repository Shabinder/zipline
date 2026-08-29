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
import kotlinx.coroutines.flow.Flow

/**
 * Carries binary payloads across the bridge.
 *
 * `bytes` travels as bytes: the JSON holds an index and the payload rides beside it as an
 * `Int8Array` the engine received with one memcpy. `base64` is what the same payload costs as text,
 * and is kept so a benchmark can price one against the other.
 */
interface BinaryEchoService : ZiplineService {
  /** Prices Zipline's envelope with no payload at all. */
  fun ping(): Int

  /** Host to guest only. Returns the size so the guest must have received it. */
  fun sinkBytes(payload: ByteArray): Int

  /** Host to guest and back. */
  fun echoBytes(payload: ByteArray): ByteArray

  /** Host to guest only, base64. The guest decodes to bytes, as a real consumer would. */
  fun sinkBase64(payload: String): Int

  /** Host to guest and back, base64, with a decode and re-encode in the guest. */
  fun echoBase64(payload: String): String

  /**
   * A plain string argument: what *every* ordinary call looks like today, since the whole call is
   * JSON text. Returns its length so the guest must have materialised it.
   */
  fun sinkString(payload: String): Int

  /**
   * The same text, sent as UTF-8 bytes and decoded in the guest. The question this answers is
   * whether moving a text payload onto the binary path is a win once the guest still has to turn
   * the bytes back into a string.
   */
  fun sinkTextAsBytes(payload: ByteArray): Int

  /** Several buffers in one call, to prove the indices line up. */
  fun concat(first: ByteArray, second: ByteArray, third: ByteArray): ByteArray

  /** A payload that may be absent, which must not consume an index. */
  fun sinkNullable(payload: ByteArray?): Int

  /** Bytes inside a returned value rather than as the value. */
  fun wrap(payload: ByteArray): BinaryEnvelope

  /** A view into a larger buffer: the offset and length must survive the crossing. */
  fun slice(payload: ByteArray, offset: Int, length: Int): ByteArray

  /** Bytes across a suspending call, whose result arrives as a separate channel call. */
  suspend fun echoSuspending(payload: ByteArray): ByteArray

  /** Bytes across a flow, which is a service under the hood: one call per emission. */
  fun stream(payload: ByteArray, count: Int): Flow<ByteArray>

  /** Throws after the arguments crossed, so the failure path is exercised with buffers in play. */
  fun boom(payload: ByteArray): ByteArray

  /** Calls back into the host mid-call, so an inner call's buffers nest inside an outer one's. */
  fun reentrant(payload: ByteArray, service: BinarySink): ByteArray
}

/**
 * Deliberately a separate type from [BinaryEchoService]: a service that takes *itself* as a
 * parameter sends the adapter generator into infinite recursion.
 */
interface BinarySink : ZiplineService {
  fun sink(payload: ByteArray): Int
}

@kotlinx.serialization.Serializable
class BinaryEnvelope(
  val label: String,
  val payload: ByteArray,
)

/**
 * The same payloads with every `ByteArray` annotated `@Contextual`.
 *
 * The plugin now routes `ByteArray` through the endpoint's `SerializersModule` whether or not it is
 * annotated, so this exists to prove the annotated form still resolves to the same serializer -
 * previously the two forms could collapse into one entry and share whichever was resolved first.
 */
interface ContextualBinaryEchoService : ZiplineService {
  fun sink(payload: @kotlinx.serialization.Contextual ByteArray): Int

  fun echo(
    payload: @kotlinx.serialization.Contextual ByteArray,
  ): @kotlinx.serialization.Contextual ByteArray
}

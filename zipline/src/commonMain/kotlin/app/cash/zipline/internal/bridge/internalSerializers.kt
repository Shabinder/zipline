/*
 * Copyright (C) 2022 Block, Inc.
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
package app.cash.zipline.internal.bridge

import kotlin.reflect.KClass
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule

/**
 * Zipline wants to use Kotlinx Serialization's [SerializersModule.getContextual] from generated
 * code, which we avoid 'cause that's an experimental API.
 *
 * Instead, we route all those calls through this helper, which also handles throwing if the
 * contextual adapter is not found.
 */
@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalSerializationApi::class)
@PublishedApi
internal fun <T : Any> SerializersModule.requireContextual(
  kClass: KClass<*>,
  typeArgumentsSerializers: List<KSerializer<*>>,
): KSerializer<T> {
  val result = getContextual(kClass, typeArgumentsSerializers)
    ?: error("No contextual serializer for $kClass is registered")
  return result as KSerializer<T>
}

/**
 * Experiment: a `@Contextual ByteArray` parameter, encoded as base64 rather than as a JSON array of
 * numbers. This exists to prove the contextual hook reaches a `ByteArray` argument at all, which is
 * what a real binary side-channel would need.
 */
@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
internal object Base64ByteArraySerializer : KSerializer<ByteArray> {
  override val descriptor =
    kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
      "app.cash.zipline.Base64ByteArray",
      kotlinx.serialization.descriptors.PrimitiveKind.STRING,
    )

  override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: ByteArray) {
    encoder.encodeString(kotlin.io.encoding.Base64.encode(value))
  }

  override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): ByteArray {
    return kotlin.io.encoding.Base64.decode(decoder.decodeString())
  }
}

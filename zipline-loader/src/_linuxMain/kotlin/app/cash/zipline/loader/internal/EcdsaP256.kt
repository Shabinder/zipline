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
@file:OptIn(ExperimentalForeignApi::class)

package app.cash.zipline.loader.internal

import kotlinx.cinterop.ExperimentalForeignApi
import okio.ByteString

/** Note that we only implement [verify] on iOS because that's all we need (currently). */
internal class EcdsaP256 : SignatureAlgorithm {
  override fun sign(message: ByteString, privateKey: ByteString): ByteString {
    error("signing is not implemented on Linux")
  }

  override fun verify(message: ByteString, signature: ByteString, publicKey: ByteString): Boolean {
    error("signing is not implemented on Linux")
  }
}

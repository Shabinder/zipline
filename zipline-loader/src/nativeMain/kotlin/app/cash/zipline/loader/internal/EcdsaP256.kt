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
    error("signing is not implemented on iOS")
  }

  override fun verify(message: ByteString, signature: ByteString, publicKey: ByteString): Boolean {
    error("signing is not implemented on iOS")
    /*val attributes = CFDictionaryCreateMutable(
      allocator = kCFAllocatorDefault,
      capacity = 0,
      keyCallBacks = kCFTypeDictionaryKeyCallBacks.ptr,
      valueCallBacks = kCFTypeDictionaryValueCallBacks.ptr,
    )
    CFDictionaryAddValue(
      attributes,
      kSecAttrKeyType,
      kSecAttrKeyTypeECSECPrimeRandom,
    )
    CFDictionaryAddValue(
      attributes,
      kSecAttrKeyClass,
      kSecAttrKeyClassPublic,
    )

    memScoped {
      val errorRef = alloc<CFErrorRefVar>()

      val publicKeyAsSecKey = publicKey.useDataRef { publicKeyDataRef ->
        SecKeyCreateWithData(
          keyData = publicKeyDataRef,
          attributes = attributes,
          error = errorRef.ptr,
        )
      }

      require(errorRef.value == null) {
        "failed to decode key: ${CFErrorGetCode(errorRef.value)}"
      }

      val result = message.sha256().useDataRef { messageSha256DataRef ->
        signature.useDataRef { signatureDataRef ->
          SecKeyVerifySignature(
            key = publicKeyAsSecKey,
            algorithm = kSecKeyAlgorithmECDSASignatureDigestX962SHA256,
            signedData = messageSha256DataRef,
            signature = signatureDataRef,
            error = errorRef.ptr,
          )
        }
      }

      require(
        errorRef.value == null ||
        CFErrorGetCode(errorRef.value) == errSecVerifyFailed.toLong(),
      ) {
        "failed to verify signature: ${CFErrorGetCode(errorRef.value)}"
      }

      return result
    }
  }

  // TODO(jwilson): is there a better way?
  private fun <T> ByteString.useDataRef(block: (CFDataRef) -> T): T {
    val byteArray = toByteArray()
    val pin = byteArray.pin()
    val bytesPointer = when {
      byteArray.isNotEmpty() -> pin.addressOf(0)
      else -> null
    }
    val nsData = NSData.dataWithBytesNoCopy(
      bytes = bytesPointer,
      length = byteArray.size.convert(),
      freeWhenDone = false,
    )
    val typeRef = CFBridgingRetain(nsData) as CFDataRef
    try {
      return block(typeRef)
    } finally {
      CFBridgingRelease(typeRef)
      pin.unpin()
    }*/
  }
}

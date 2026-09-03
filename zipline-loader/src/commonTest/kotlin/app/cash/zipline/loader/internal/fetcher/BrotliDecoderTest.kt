/*
 * Copyright (C) 2026 Shabinder Singh.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package app.cash.zipline.loader.internal.fetcher

import kotlin.test.Test
import kotlin.test.assertEquals
import okio.ByteString.Companion.decodeBase64

class BrotliDecoderTest {
  @Test
  fun decodesKnownBrotliFrame() {
    val compressed = checkNotNull("HxYA+KXL0Nje1CDoVAnrLHUQ".decodeBase64())
    assertEquals("hello compression world", compressed.decodeBrotli().utf8())
  }
}

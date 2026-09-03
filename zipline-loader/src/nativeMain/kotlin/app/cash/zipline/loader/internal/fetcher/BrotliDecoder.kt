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

import com.ensody.kompressor.brotli.BrotliDecompressor
import com.ensody.kompressor.core.transform
import okio.ByteString
import okio.ByteString.Companion.toByteString

/** Uses Kompressor's Brotli C bindings on Kotlin/Native targets. */
internal actual fun ByteString.decodeBrotli(): ByteString =
  BrotliDecompressor().transform(toByteArray()).toByteString()

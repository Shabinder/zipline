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

import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.brotli.dec.BrotliInputStream

/** Uses the pure-Java decoder on Android and JVM, preserving Zipline's Java 11 runtime floor. */
internal actual fun ByteString.decodeBrotli(): ByteString =
  BrotliInputStream(toByteArray().inputStream()).use { it.readBytes().toByteString() }

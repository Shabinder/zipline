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

private object ReturnedPlaybackSourceState {
  var activeSourceCount = 0
  var closedSourceCount = 0
}

private class JsReturnedPlaybackSourceFactory : ReturnedPlaybackSourceFactory {
  override suspend fun open(seed: Int): ReturnedPlaybackSource {
    ReturnedPlaybackSourceState.activeSourceCount++
    return JsReturnedPlaybackSource(seed)
  }
}

private class JsReturnedPlaybackSource(
  private val seed: Int,
) : ReturnedPlaybackSource {
  private var closed = false
  private var failNextRead = false
  private var lastSeekPositionMs = 0L
  private val payloads = mutableMapOf<Pair<Long, Int>, ByteArray>()

  override suspend fun descriptor() = ReturnedPlaybackDescriptor(
    contentLength = CONTENT_LENGTH,
    durationMs = DURATION_MS,
    mimeType = "audio/mp4",
    lastSeekPositionMs = lastSeekPositionMs,
  )

  override suspend fun read(offset: Long, maxBytes: Int): ByteArray {
    check(!closed) { "playback source is closed" }
    require(offset >= 0L) { "offset must be non-negative" }
    require(maxBytes >= 0) { "maxBytes must be non-negative" }
    if (failNextRead) {
      failNextRead = false
      error("injected playback read failure")
    }
    if (offset >= CONTENT_LENGTH || maxBytes == 0) return byteArrayOf()
    val length = minOf(maxBytes.toLong(), CONTENT_LENGTH - offset).toInt()
    return payloads.getOrPut(offset to length) {
      val offsetInPattern = (offset % PLAYBACK_BYTE_PATTERN_SIZE).toInt()
      ByteArray(length) { index -> expectedPlaybackByte(seed, offsetInPattern, index) }
    }
  }

  override suspend fun readText(offset: Long, maxBytes: Int): String = read(offset, maxBytes).decodeToString()

  override suspend fun seek(positionMs: Long) {
    check(!closed) { "playback source is closed" }
    lastSeekPositionMs = positionMs.coerceIn(0L, DURATION_MS)
  }

  override suspend fun failNextRead() {
    check(!closed) { "playback source is closed" }
    failNextRead = true
  }

  override fun close() {
    if (closed) return
    closed = true
    payloads.clear()
    ReturnedPlaybackSourceState.activeSourceCount--
    ReturnedPlaybackSourceState.closedSourceCount++
  }

  private companion object {
    const val CONTENT_LENGTH = 4L * 1024L * 1024L
    const val DURATION_MS = 240_000L
  }
}

private class JsReturnedPlaybackSourceMonitor : ReturnedPlaybackSourceMonitor {
  override fun activeSourceCount() = ReturnedPlaybackSourceState.activeSourceCount
  override fun closedSourceCount() = ReturnedPlaybackSourceState.closedSourceCount
}

private const val PLAYBACK_BYTE_PATTERN_SIZE = 95

internal fun expectedPlaybackByte(seed: Int, offsetInPattern: Int, index: Int): Byte = ((seed + offsetInPattern + index) % PLAYBACK_BYTE_PATTERN_SIZE + 32).toByte()

private val returnedPlaybackZipline by lazy { Zipline.get() }

@JsExport
fun prepareReturnedPlaybackSourceJsBridges() {
  returnedPlaybackZipline.bind<ReturnedPlaybackSourceFactory>(
    "returnedPlaybackSourceFactory",
    JsReturnedPlaybackSourceFactory(),
  )
  returnedPlaybackZipline.bind<ReturnedPlaybackSourceMonitor>(
    "returnedPlaybackSourceMonitor",
    JsReturnedPlaybackSourceMonitor(),
  )
}

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
import kotlinx.serialization.Serializable

interface ReturnedPlaybackSourceFactory : ZiplineService {
  suspend fun open(seed: Int): ReturnedPlaybackSource
}

interface ReturnedPlaybackSource : ZiplineService {
  suspend fun descriptor(): ReturnedPlaybackDescriptor
  suspend fun read(offset: Long, maxBytes: Int): ByteArray
  suspend fun readText(offset: Long, maxBytes: Int): String
  suspend fun seek(positionMs: Long)
  suspend fun failNextRead()
}

interface ReturnedPlaybackSourceMonitor : ZiplineService {
  fun activeSourceCount(): Int
  fun closedSourceCount(): Int
}

@Serializable
data class ReturnedPlaybackDescriptor(
  val contentLength: Long,
  val durationMs: Long,
  val mimeType: String,
  val lastSeekPositionMs: Long,
)

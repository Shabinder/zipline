/*
 * Copyright (C) 2025 Cash App
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

class JsUnitService : UnitService {
  private var count = 0

  override fun call() {
    count++
  }

  override suspend fun callSuspending() {
    count++
  }

  override fun count(): Int = count

  override fun nullableUnitReturnsNull(): Unit? = null

  override fun nullableUnitReturnsUnit(): Unit? = Unit

  override suspend fun nullableUnitReturnsNullSuspending(): Unit? = null

  override suspend fun nullableUnitReturnsUnitSuspending(): Unit? = Unit
}

private val zipline by lazy { Zipline.get() }

@JsExport
fun prepareUnitServiceJsBridges() {
  zipline.bind<UnitService>("unitService", JsUnitService())
}

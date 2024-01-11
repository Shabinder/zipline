/*
 * Copyright (C) 2024 Cash App
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
package app.cash.zipline

import app.cash.zipline.testing.EchoRequest
import app.cash.zipline.testing.EchoResponse
import app.cash.zipline.testing.EchoService
import app.cash.zipline.testing.ServiceBuilder
import app.cash.zipline.testing.ServiceBuilderSerializersModule
import app.cash.zipline.testing.loadTestingJs
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Confirm we can build services of serializable types, as described in
 * [ziplineServiceSerializer]. This test confirms it all works across bridges.
 */
class ServiceBuilderTest {

  @OptIn(ExperimentalCoroutinesApi::class)
  private val dispatcher = UnconfinedTestDispatcher()
  private val zipline = Zipline.create(dispatcher, ServiceBuilderSerializersModule)

  @BeforeTest
  fun setUp() = runBlocking(dispatcher) {
    zipline.loadTestingJs()
  }

  @AfterTest
  fun tearDown() = runBlocking(dispatcher) {
    zipline.close()
  }

  @Test
  fun serviceMember() = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareServiceBuilderJsBridges()")

    val builder = zipline.take<ServiceBuilder>("serviceBuilder")
    val helloGreetEchoService = builder.buildServiceInstance("Hello!")
    val bonjourGreetEchoService = builder.buildServiceInstance("Bonjour!")

    assertThat(helloGreetEchoService.echo(EchoRequest(message = "by Shabinder")))
      .isEqualTo(EchoResponse("Hello! from JavaScript, by Shabinder"))
    assertThat(bonjourGreetEchoService.echo(EchoRequest(message = "by Shabinder")))
      .isEqualTo(EchoResponse("Bonjour! from JavaScript, by Shabinder"))

    helloGreetEchoService.close()
    bonjourGreetEchoService.close()
    builder.close()
  }

  private class JvmEchoService : EchoService {
    override fun echo(request: EchoRequest) = EchoResponse("JVM received '${request.message}'")
  }
}

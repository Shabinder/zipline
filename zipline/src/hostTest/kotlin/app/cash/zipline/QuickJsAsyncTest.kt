/*
 * Copyright (C) 2024 SoundBound Contributors
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

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for JavaScript Promise/async-await support via executePendingJobs.
 *
 * These tests verify that Kotlin code can properly interact with JavaScript
 * Promises by processing the microtask queue.
 */
class QuickJsAsyncTest {
  private val quickJs = QuickJs.create()

  @AfterTest
  fun tearDown() {
    quickJs.close()
  }

  @Test
  fun executePendingJobsWithNoJobs() {
    // No pending jobs should return 0
    val result = quickJs.executePendingJobs()
    assertEquals(0, result)
  }

  @Test
  fun executePendingJobsWithResolvedPromise() {
    // Create a promise that resolves immediately and sets a global variable
    quickJs.evaluate(
      """
      globalThis.result = null;
      Promise.resolve('hello').then(function(value) {
        globalThis.result = value;
      });
      """
    )

    // Before executing pending jobs, result should be null
    val beforeResult = quickJs.evaluate("globalThis.result;")
    assertEquals(null, beforeResult)

    // Execute pending jobs - this should process the promise callback
    val jobsExecuted = quickJs.executePendingJobs()
    assertTrue(jobsExecuted >= 1, "Expected at least 1 job to be executed, got $jobsExecuted")

    // After executing pending jobs, result should be 'hello'
    val afterResult = quickJs.evaluate("globalThis.result;")
    assertEquals("hello", afterResult)
  }

  @Test
  fun executePendingJobsWithPromiseChain() {
    // Create a promise chain
    quickJs.evaluate(
      """
      globalThis.results = [];
      Promise.resolve(1)
        .then(function(v) { globalThis.results.push('first:' + v); return v + 1; })
        .then(function(v) { globalThis.results.push('second:' + v); return v + 1; })
        .then(function(v) { globalThis.results.push('third:' + v); });
      """
    )

    // Execute all pending jobs
    while (quickJs.executePendingJobs() > 0) {
      // Continue until all jobs are processed
    }

    // Verify the promise chain executed in order
    val results = quickJs.evaluate("globalThis.results;") as Array<*>
    assertEquals(3, results.size)
    assertEquals("first:1", results[0])
    assertEquals("second:2", results[1])
    assertEquals("third:3", results[2])
  }

  @Test
  fun executePendingJobsWithAsyncAwait() {
    // Test async/await syntax
    quickJs.evaluate(
      """
      globalThis.result = null;
      globalThis.runAsync = async function() {
        const value = await Promise.resolve('async-result');
        globalThis.result = value;
        return value;
      };
      globalThis.runAsync();
      """
    )

    // Execute pending jobs to complete the async function
    while (quickJs.executePendingJobs() > 0) {
      // Continue until all jobs are processed
    }

    val result = quickJs.evaluate("globalThis.result;")
    assertEquals("async-result", result)
  }

  @Test
  fun executePendingJobsWithAwaitedImmediateValue() {
    // BgUtils' BotGuardClient awaits vm.a(...) even when that VM returns an array immediately.
    // Awaiting a non-Promise must still suspend once and resume from the QuickJS job queue.
    quickJs.evaluate(
      """
      globalThis.result = null;
      globalThis.runAsync = async function() {
        globalThis.result = await ['vm-ready']?.[0];
      };
      globalThis.runAsync();
      """,
    )

    assertEquals(null, quickJs.evaluate("globalThis.result;"))
    assertTrue(quickJs.executePendingJobs() >= 1)
    assertEquals("vm-ready", quickJs.evaluate("globalThis.result;"))
  }

  @Test
  fun executePendingJobsWithMultiplePromises() {
    // Create multiple independent promises
    quickJs.evaluate(
      """
      globalThis.results = [];
      Promise.resolve('a').then(function(v) { globalThis.results.push(v); });
      Promise.resolve('b').then(function(v) { globalThis.results.push(v); });
      Promise.resolve('c').then(function(v) { globalThis.results.push(v); });
      """
    )

    // Execute all pending jobs
    while (quickJs.executePendingJobs() > 0) {
      // Continue until all jobs are processed
    }

    val results = quickJs.evaluate("globalThis.results;") as Array<*>
    assertEquals(3, results.size)
    // All results should be present (order may vary based on microtask scheduling)
    assertTrue(results.contains("a"))
    assertTrue(results.contains("b"))
    assertTrue(results.contains("c"))
  }

  @Test
  fun executePendingJobsWithDelayedResolve() {
    // Simulate a deferred promise resolution pattern
    quickJs.evaluate(
      """
      globalThis.result = null;
      globalThis.resolver = null;

      var promise = new Promise(function(resolve, reject) {
        globalThis.resolver = resolve;
      });

      promise.then(function(value) {
        globalThis.result = value;
      });
      """
    )

    // Execute pending jobs - nothing should happen yet since promise isn't resolved
    quickJs.executePendingJobs()
    assertEquals(null, quickJs.evaluate("globalThis.result;"))

    // Now resolve the promise
    quickJs.evaluate("globalThis.resolver('deferred-value');")

    // Execute pending jobs - now the .then callback should run
    while (quickJs.executePendingJobs() > 0) {
      // Continue
    }

    assertEquals("deferred-value", quickJs.evaluate("globalThis.result;"))
  }

  @Test
  fun executePendingJobsWithRejectedPromise() {
    // Test that rejected promises are handled correctly
    quickJs.evaluate(
      """
      globalThis.result = null;
      globalThis.error = null;

      Promise.reject(new Error('test error'))
        .then(function(v) { globalThis.result = v; })
        .catch(function(e) { globalThis.error = e.message; });
      """
    )

    // Execute pending jobs
    while (quickJs.executePendingJobs() > 0) {
      // Continue
    }

    assertEquals(null, quickJs.evaluate("globalThis.result;"))
    assertEquals("test error", quickJs.evaluate("globalThis.error;"))
  }

  @Test
  fun executePendingJobsWithNestedPromises() {
    // Test nested promises
    quickJs.evaluate(
      """
      globalThis.result = null;

      Promise.resolve().then(function() {
        return Promise.resolve().then(function() {
          return Promise.resolve('nested');
        });
      }).then(function(value) {
        globalThis.result = value;
      });
      """
    )

    // Execute all pending jobs
    while (quickJs.executePendingJobs() > 0) {
      // Continue
    }

    assertEquals("nested", quickJs.evaluate("globalThis.result;"))
  }

  /**
   * Test that QuickJS can handle the "} else\n while" pattern that Kotlin/JS generates.
   * This was a regression in older QuickJS versions.
   */
  @Test
  fun elseFollowedByWhileOnNewLineIsSupported() {
    // This pattern is generated by Kotlin/JS compiler and older QuickJS versions fail to parse it
    val code = """
      var x = true;
      var result = 'not executed';
      if (x) {
        result = 'then';
      } else
      while (false) {
        result = 'else-while';
      }
      globalThis.testResult = result;
    """.trimIndent()

    // If compile fails with "unexpected token in expression: 'else'", QuickJS is too old
    val bytecode = quickJs.compile(code, "test.js")
    assertTrue(bytecode.isNotEmpty(), "Compilation should succeed")
    quickJs.execute(bytecode)
    assertEquals("then", quickJs.evaluate("globalThis.testResult;"))
  }
}

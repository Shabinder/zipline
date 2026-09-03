/*
 * Copyright (C) 2015 Square, Inc.
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

import androidx.annotation.Keep
import java.util.regex.Pattern

@Keep // Instruct ProGuard not to strip this type.
actual class QuickJsException @JvmOverloads constructor(
  detailMessage: String,
  jsStackTrace: String? = null,
) : RuntimeException(detailMessage) {
  init {
    if (jsStackTrace != null) {
      addJavaScriptStack(jsStackTrace)
    }
  }

  private companion object {
    /**
     * QuickJs stack trace strings have multiple lines of the format "at func (file.ext:line)".
     * "func" is optional, but we'll omit frames without a function, since it means the frame is in
     * native code.
     */
    /**
     * Matches one QuickJS stack frame: `    at f1 (explode.js:2:57)`.
     *
     * Line and column are captured separately rather than being swept into the file name.
     * QuickJS 2026-06-04 appends a column to every frame where 2021-03-27 emitted only a line,
     * and the previous pattern captured the whole `file:line[:column]` blob as the file - so the
     * added column silently changed every rendered frame from `(explode.js:2)` to
     * `(explode.js:2:57)`. Parsing the parts also means the line number lands in
     * StackTraceElement's lineNumber field, where it belongs.
     *
     * Frames with no location at all (`at JavaScript.disconnect(native)`) still match, with no
     * line number.
     */
    private val STACK_TRACE_PATTERN =
      Pattern.compile("\\s*at ([^\\s]+) \\((.+(?<!cpp))\\).*$")

    /** Trailing `:123` on a location, stripped one at a time from the right. */
    private val TRAILING_NUMBER = Regex(":(\\d+)$")

    /** Java StackTraceElements require a class name.  We don't have one in JS, so use this.  */
    private const val STACK_TRACE_CLASS_NAME = "JavaScript"

    /**
     * Parses `StackTraceElement`s from `detailMessage` and adds them to the proper place
     * in `throwable`'s stack trace.
     *
     * NOTE: This method is also called from native code.
     */
    @JvmStatic // Expose for easy invocation from native.
    @JvmSynthetic // Hide from public API to Java consumers.
    fun Throwable.addJavaScriptStack(detailMessage: String) {
      val lines = detailMessage.split('\n').dropLastWhile(String::isEmpty)
      if (lines.isEmpty()) {
        return
      }
      // We have a stacktrace following the message. Add it to the exception.
      val elements = mutableListOf<StackTraceElement>()

      // Splice the JavaScript stack in right above the call to QuickJs.
      var spliced = false
      for (stackTraceElement in stackTrace) {
        if (!spliced &&
          stackTraceElement.isNativeMethod &&
          stackTraceElement.isZipline
        ) {
          spliced = true
          for (line in lines) {
            val jsElement = toStackTraceElement(line) ?: continue
            elements += jsElement
          }
        }
        elements += stackTraceElement
      }
      stackTrace = elements.toTypedArray()
    }

    private val StackTraceElement.isZipline: Boolean
      get() = className == QuickJs::class.java.name || className == JniCallChannel::class.java.name

    private fun toStackTraceElement(s: String): StackTraceElement? {
      val m = STACK_TRACE_PATTERN.matcher(s)
      return if (!m.matches()) {
        null // Nothing interesting on this line.
      } else {
        // QuickJS 2026-06-04 renders `file:line:column`; 2021-03-27 rendered `file:line`; and a
        // frame with no location at all is just `native`. Strip the numeric suffixes from the
        // RIGHT rather than matching the file with one pattern - Windows paths contain a colon
        // (`C:\\Documents\\myFile.js:8`), so any file pattern that forbids colons drops those
        // frames entirely.
        var file = m.group(2)
        var line = -1
        TRAILING_NUMBER.find(file)?.let { last ->
          val withoutLast = file.substring(0, last.range.first)
          val secondLast = TRAILING_NUMBER.find(withoutLast)
          if (secondLast != null) {
            // file:line:column - the one we just stripped was the column.
            line = secondLast.groupValues[1].toInt()
            file = withoutLast.substring(0, secondLast.range.first)
          } else {
            line = last.groupValues[1].toInt()
            file = withoutLast
          }
        }
        StackTraceElement(STACK_TRACE_CLASS_NAME, m.group(1), file, line)
      }
    }
  }
}

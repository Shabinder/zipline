/*
 * Copyright (C) 2021 Square, Inc.
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
package app.cash.zipline.bytecode

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import okio.Buffer
import okio.ByteString.Companion.decodeHex
import org.junit.Test

class LineNumbersTest {
  /**
   * The QuickJS 2026-06-04 pc2line encoding, byte by byte:
   *
   *  - `00` leb128 line, `00` leb128 column - the function's own position, each stored biased by
   *    one, so both decode to 1. This is the head of the table; before 2026-06-04 the line lived
   *    in a separate field on the debug record and columns were not stored at all.
   *  - `1f` a short-form entry: `(diffLine - PC2LINE_BASE) + diffPc * PC2LINE_RANGE +
   *    PC2LINE_OP_FIRST` = `0 + 6*5 + 1`, so pc advances 6 and line falls by 1.
   *  - `00` sleb128 column delta 0.
   *  - `00` escape to the long form, `08` leb128 pc delta 8, `12` sleb128 line delta 9 (zigzag).
   *  - `00` sleb128 column delta 0.
   */
  private val goldenValue = "00001f0000081200".decodeHex()

  @Test
  fun readGoldenValue() {
    val reader = LineNumberReader(Buffer().write(goldenValue))
    assertThat(reader.line).isEqualTo(1)
    assertThat(reader.column).isEqualTo(1)
    assertThat(reader.next()).isTrue()
    assertThat(reader.pc).isEqualTo(6)
    assertThat(reader.line).isEqualTo(0)
    assertThat(reader.next()).isTrue()
    assertThat(reader.pc).isEqualTo(14)
    assertThat(reader.line).isEqualTo(9)
    assertThat(reader.next()).isFalse()
  }

  @Test
  fun writeGoldenValue() {
    val buffer = Buffer()
    LineNumberWriter(functionLineNumber = 1, functionColumnNumber = 1, sink = buffer).use { writer ->
      writer.next(pc = 6, line = 0, column = 1)
      writer.next(pc = 14, line = 9, column = 1)
    }
    assertThat(buffer.readByteString()).isEqualTo(goldenValue)
  }

  @Test
  fun columnsRoundTrip() {
    val buffer = Buffer()
    LineNumberWriter(functionLineNumber = 4, functionColumnNumber = 7, sink = buffer).use { writer ->
      writer.next(pc = 3, line = 4, column = 12) // Column moves while the line stands still.
      writer.next(pc = 9, line = 5, column = 2) // Both move, and the column moves backwards.
    }
    val encoded = buffer.readByteString()

    val reader = LineNumberReader(Buffer().write(encoded))
    assertThat(reader.line).isEqualTo(4)
    assertThat(reader.column).isEqualTo(7)
    assertThat(reader.next()).isTrue()
    assertThat(reader.pc).isEqualTo(3)
    assertThat(reader.line).isEqualTo(4)
    assertThat(reader.column).isEqualTo(12)
    assertThat(reader.next()).isTrue()
    assertThat(reader.pc).isEqualTo(9)
    assertThat(reader.line).isEqualTo(5)
    assertThat(reader.column).isEqualTo(2)
    assertThat(reader.next()).isFalse()
  }
}

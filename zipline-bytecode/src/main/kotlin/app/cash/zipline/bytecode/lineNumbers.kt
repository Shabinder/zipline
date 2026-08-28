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

import okio.BufferedSink
import okio.BufferedSource
import okio.Closeable

/**
 * Decode the pc2line table.
 *
 * Since QuickJS 2026-06-04 the table opens with the function's own line and column, and every
 * entry carries a column delta after the line delta. Before that the starting line lived in a
 * separate field on the debug record and columns were not tracked at all.
 */
class LineNumberReader(
  private val source: BufferedSource,
) : Closeable by source {
  var pc: Int = 0
    private set

  /** Line of the current instruction, or of the function itself before the first [next]. */
  var line: Int = 0
    private set

  /** Column of the current instruction, or of the function itself before the first [next]. */
  var column: Int = 0
    private set

  init {
    if (!source.exhausted()) {
      // Both are stored biased by one; QuickJS's own find_line_num() adds it back.
      line = source.readLeb128() + 1
      column = source.readLeb128() + 1
    }
  }

  fun next(): Boolean {
    if (source.exhausted()) return false

    val op = source.readByte().toInt() and 0xff
    val diffPc: Int
    val diffLine: Int
    if (op != 0) {
      val parts = op - PC2LINE_OP_FIRST
      diffPc = (parts / PC2LINE_RANGE)
      diffLine = (parts % PC2LINE_RANGE) + PC2LINE_BASE
    } else {
      diffPc = source.readLeb128()
      diffLine = source.readSleb128()
    }
    pc += diffPc
    line += diffLine
    column += source.readSleb128()

    return true
  }
}

/**
 * Encode a pc2line table.
 *
 * The function's own line and column are written first, as QuickJS 2026-06-04 expects; each
 * subsequent entry carries a column delta after the line delta.
 */
class LineNumberWriter(
  functionLineNumber: Int,
  functionColumnNumber: Int,
  private val sink: BufferedSink,
) : Closeable by sink {
  private var lastPc = 0
  private var lastLine = functionLineNumber
  private var lastColumn = functionColumnNumber

  init {
    // Stored biased by one, to match QuickJS. See LineNumberReader.
    sink.writeLeb128(functionLineNumber - 1)
    sink.writeLeb128(functionColumnNumber - 1)
  }

  fun next(pc: Int, line: Int, column: Int) {
    if (line < 0) return // Drop negative line numbers.

    val diffPc = pc - lastPc
    val diffLine = line - lastLine
    val diffColumn = column - lastColumn

    if (diffLine == 0 && diffColumn == 0) return // Nothing to do.
    if (diffPc < 0) return // PC may only advance.

    val linePart = diffLine - PC2LINE_BASE
    if (linePart in 0 until PC2LINE_RANGE && diffPc <= PC2LINE_DIFF_PC_MAX) {
      val pcPart = diffPc * PC2LINE_RANGE
      sink.writeByte(linePart + pcPart + PC2LINE_OP_FIRST)
    } else {
      sink.writeByte(0)
      sink.writeLeb128(diffPc)
      sink.writeSleb128(diffLine)
    }
    sink.writeSleb128(diffColumn)

    lastPc = pc
    lastLine = line
    lastColumn = column
  }
}

/* for the encoding of the pc2line table */
private const val PC2LINE_BASE = -1
private const val PC2LINE_RANGE = 5
private const val PC2LINE_OP_FIRST = 1
private const val PC2LINE_DIFF_PC_MAX = ((255 - PC2LINE_OP_FIRST) / PC2LINE_RANGE)

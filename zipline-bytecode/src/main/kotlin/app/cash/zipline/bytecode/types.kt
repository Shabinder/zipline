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

import okio.ByteString
import okio.ByteString.Companion.encode
import okio.ByteString.Companion.encodeUtf8
import okio.utf8Size

sealed class JsObject

object JsNull : JsObject()

object JsUndefined : JsObject()

data class JsBoolean(val value: Boolean) : JsObject()

data class JsInt(val value: Int) : JsObject()

data class JsDouble(val value: Double) : JsObject()

data class JsString(
  val isWideChar: Boolean,
  val bytes: ByteString,
) : JsObject() {
  val string: String
    get() = when {
      isWideChar -> bytes.string(Charsets.UTF_16LE)
      else -> bytes.utf8()
    }
}

fun String.toJsString(): JsString {
  return when (length) {
    utf8Size().toInt() -> JsString(isWideChar = false, bytes = encodeUtf8())
    else -> JsString(isWideChar = true, bytes = encode(Charsets.UTF_16LE))
  }
}

data class JsFunctionBytecode(
  val flags: Int,
  val jsMode: Byte,
  val name: String,
  val argCount: Int,
  val varCount: Int,
  val definedArgCount: Int,
  val stackSize: Int,
  /** Number of variable references; added to the record in QuickJS 2026-06-04. */
  val varRefCount: Int,
  val locals: List<JsVarDef>,
  val closureVars: List<JsClosureVar>,
  val bytecode: ByteString,
  val constantPool: List<JsObject>,
  val debug: Debug?,
) : JsObject() {
  val hasPrototype get() = flags.bit(0)
  val hasSimpleParameterList get() = flags.bit(1)
  val isDerivedClassConstructor get() = flags.bit(2)
  val needHomeObject get() = flags.bit(3)
  val kind get() = flags.bits(bit = 4, bitCount = 2)
  val newTargetAllowed get() = flags.bit(6)
  val superCallAllowed get() = flags.bit(7)
  val superAllowed get() = flags.bit(8)
  val argumentsAllowed get() = flags.bit(9)
  val hasDebug get() = flags.bit(10)
  /** Called `backtrace_barrier` before QuickJS 2026-06-04; the bit position is unchanged. */
  val isDirectOrIndirectEval get() = flags.bit(11)
}

data class JsVarDef(
  val name: String,
  val scopeNext: Int,
  /** Index into the function's variable references; added in QuickJS 2026-06-04. */
  val varRefIndex: Int,
  // JsVarKindEnum
  val kind: Int,
  val isConst: Boolean,
  val isLexical: Boolean,
  val isCaptured: Boolean,
  /** Added in QuickJS 2026-06-04, which also dropped the separate `scopeLevel` field. */
  val hasScope: Boolean,
)

data class JsClosureVar(
  val name: String,
  val varIndex: Int,
  /**
   * Replaced the separate `isLocal` and `isArg` booleans in QuickJS 2026-06-04, which also widened
   * the flags field from one byte to two. See `JSClosureTypeEnum` in `quickjs.c`.
   */
  val closureType: Int,
  val isConst: Boolean,
  val isLexical: Boolean,
  // JsVarKindEnum
  val kind: Int,
)

data class Debug(
  val fileName: String,
  /**
   * The pc2line table. Since QuickJS 2026-06-04 this begins with the function's own line and
   * column, which used to be a separate `lineNumber` field on this record, and each entry carries
   * a column delta as well as a line delta. See [LineNumberReader] and [LineNumberWriter].
   */
  val pc2Line: ByteString,
  /** Original source text, retained by QuickJS 2026-06-04 for `Function.prototype.toString`. */
  val source: ByteString,
)

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
package app.cash.zipline.cli

import app.cash.zipline.QuickJs
import app.cash.zipline.ZiplineManifest
import app.cash.zipline.bytecode.SourceMap
import app.cash.zipline.bytecode.applySourceMapToBytecode
import app.cash.zipline.bytecode.clean
import app.cash.zipline.bytecode.stripLineNumbers
import app.cash.zipline.loader.CURRENT_ZIPLINE_VERSION
import app.cash.zipline.loader.ManifestSigner
import app.cash.zipline.loader.ZiplineFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.toByteString
import okio.HashingSink
import okio.buffer
import okio.sink

internal class ZiplineCompiler(
  private val outputDir: File,
  private val mainFunction: String?,
  private val mainModuleId: String?,
  private val manifestSigner: ManifestSigner?,
  private val version: String?,
  private val metadata: Map<String, String>,
  private val stripLineNumbers: Boolean,
) {
  companion object {
    private const val MODULE_PATH_PREFIX = "./"
    private const val ZIPLINE_EXTENSION = ".zipline"
  }

  fun compile(
    inputDir: File,
  ) {
    val jsFiles = getJsFiles(inputDir.listFiles()!!.asList())
    val modules = compileFilesInParallel(jsFiles)
    writeManifest(
      modules = modules,
    )
  }

  fun incrementalCompile(
    modifiedFiles: List<File>,
    addedFiles: List<File>,
    removedFiles: List<File>,
  ) {
    val modifiedFileNames = getJsFiles(modifiedFiles).map { it.name }.toSet()
    val removedFileNames = getJsFiles(removedFiles).map { it.name }.toSet()

    // Get the current manifest and remove any removed or modified modules.
    val manifestFile = File(outputDir.path, manifestFileName)
    val manifest = Json.decodeFromString<ZiplineManifest>(manifestFile.readText())
    val unchangedModules = manifest.modules.filter { (k, _) ->
      val moduleFileName = k.removePrefix(MODULE_PATH_PREFIX)
      moduleFileName !in removedFileNames && moduleFileName !in modifiedFileNames
    }

    // Delete Zipline files for any removed JS files.
    removedFileNames.forEach {
      File(outputDir.path + "/" + it.removeSuffix(".js") + ZIPLINE_EXTENSION).delete()
    }

    // Compile the newly added or modified files and add them into the module list.
    val addedOrModifiedFiles = getJsFiles(addedFiles) + getJsFiles(modifiedFiles)
    val compiledModules = compileFilesInParallel(addedOrModifiedFiles)

    // Write back a new up-to-date manifest.
    writeManifest(
      modules = unchangedModules + compiledModules,
    )
  }

  private fun compileFilesInParallel(
    files: List<File>,
  ) = runBlocking {
    files
      .map { file ->
        async(Dispatchers.Default) {
          compileSingleFile(file)
        }
      }
      .awaitAll()
      .toMap()
  }

  private fun compileSingleFile(
    jsFile: File,
  ): Pair<String, ZiplineManifest.Module> {
    val jsSourceMapFile = File("${jsFile.path}.map")
    val outputZiplineFilePath = jsFile.nameWithoutExtension + ZIPLINE_EXTENSION
    val outputZiplineFile = File(outputDir.path, outputZiplineFilePath)

    val quickJs = QuickJs.create()
    quickJs.use {
      val jsContent = jsFile.readText()
      var bytecode = try {
        quickJs.compile(jsContent, jsFile.name)
      } catch (e: Exception) {
        // Use Node.js --check for better syntax error messages with exact line numbers
        val nodeErrorInfo = runNodeSyntaxCheck(jsFile)

        val lines = jsContent.lines()

        // Build comprehensive error message
        val context = buildString {
          appendLine("=".repeat(80))
          appendLine("QUICKJS COMPILATION ERROR")
          appendLine("=".repeat(80))
          appendLine("File: ${jsFile.absolutePath}")
          appendLine("Total lines: ${lines.size}")
          appendLine()
          appendLine("QuickJS Error: ${e.message}")
          appendLine()

          if (nodeErrorInfo != null) {
            appendLine("=== NODE.JS SYNTAX CHECK (more precise) ===")
            appendLine(nodeErrorInfo.errorOutput)
            appendLine()

            if (nodeErrorInfo.errorLine > 0 && nodeErrorInfo.errorLine <= lines.size) {
              appendLine("=== CONTEXT (lines ${maxOf(1, nodeErrorInfo.errorLine - 3)} to ${minOf(lines.size, nodeErrorInfo.errorLine + 3)}) ===")
              for (i in maxOf(0, nodeErrorInfo.errorLine - 4) until minOf(lines.size, nodeErrorInfo.errorLine + 3)) {
                val marker = if (i == nodeErrorInfo.errorLine - 1) ">>> " else "    "
                appendLine("$marker${i + 1}: ${lines[i].take(200)}")
              }
            }
          } else {
            appendLine("=== NODE.JS NOT AVAILABLE - Using QuickJS error only ===")
            appendLine("TIP: Install Node.js for more precise syntax error messages")
            appendLine("     Run manually: node --check ${jsFile.absolutePath}")
          }

          appendLine()
          appendLine("=== COMMON CAUSES ===")
          appendLine("1. Comments between '}' and 'else' in Kotlin js() blocks - move comments INSIDE blocks")
          appendLine("2. Semicolons before 'else' statements (};  else)")
          appendLine("3. Incomplete/malformed JavaScript in js() raw strings")
          appendLine("=".repeat(80))
        }

        // Write to temp file for debugging
        File("/tmp/quickjs_error.txt").writeText(context)

        throw RuntimeException(context, e)
      }

      if (jsSourceMapFile.exists()) {
        // Rewrite the bytecode with source line numbers.
        val sourceMap = SourceMap.parse(jsSourceMapFile.readText()).clean()
        bytecode = applySourceMapToBytecode(bytecode, sourceMap)
      }

      if (stripLineNumbers) {
        bytecode = stripLineNumbers(bytecode)
      }

      val ziplineFile = ZiplineFile(CURRENT_ZIPLINE_VERSION, bytecode.toByteString())
      val sha256 = outputZiplineFile.sink().use { fileSink ->
        val hashingSink = HashingSink.sha256(fileSink)
        hashingSink.buffer().use {
          ziplineFile.writeTo(it)
        }
        hashingSink.hash
      }

      val dependencies = collectDependencies(quickJs, bytecode)

      return "$MODULE_PATH_PREFIX${jsFile.name}" to ZiplineManifest.Module(
        url = outputZiplineFilePath,
        sha256 = sha256,
        dependsOnIds = dependencies,
      )
    }
  }

  private fun writeManifest(
    modules: Map<String, ZiplineManifest.Module>,
  ) {
    val unsignedManifest = ZiplineManifest.create(
      modules = modules,
      mainFunction = mainFunction,
      mainModuleId = mainModuleId,
      version = version,
      metadata = metadata,
    )

    val manifest = manifestSigner?.sign(unsignedManifest) ?: unsignedManifest

    val manifestFile = File(outputDir.path, manifestFileName)
    manifestFile.writeText(manifest.encodeJson())
  }

  private fun getJsFiles(files: List<File>) = files.filter { it.path.endsWith(".js") }

  @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER") // Access :zipline internals.
  private val manifestFileName = app.cash.zipline.loader.internal.MANIFEST_FILE_NAME

  @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER") // Access :zipline internals.
  private fun collectDependencies(quickJs: QuickJs, bytecode: ByteArray): List<String> {
    app.cash.zipline.internal.collectModuleDependencies(quickJs)
    quickJs.execute(bytecode)
    return app.cash.zipline.internal.getModuleDependencies(quickJs)
  }

  /**
   * Data class holding Node.js syntax check results.
   */
  private data class NodeErrorInfo(
    val errorLine: Int,
    val errorColumn: Int,
    val errorOutput: String,
  )

  /**
   * Run Node.js --check on the file to get precise syntax error location.
   * Node.js provides better error messages with exact line:column numbers.
   *
   * @return NodeErrorInfo with parsed error details, or null if Node.js unavailable
   */
  private fun runNodeSyntaxCheck(jsFile: File): NodeErrorInfo? {
    return try {
      val process = ProcessBuilder("node", "--check", jsFile.absolutePath)
        .redirectErrorStream(true)
        .start()

      val output = process.inputStream.bufferedReader().readText()
      val exitCode = process.waitFor()

      if (exitCode == 0) {
        // No syntax error according to Node.js
        return null
      }

      // Parse line number from Node.js error output
      // Format: "/path/to/file.js:5247\n         else if..."
      // Or: "SyntaxError: ... at line 5247"
      val lineRegex = Regex("""${Regex.escape(jsFile.absolutePath)}:(\d+)(?::(\d+))?""")
      val match = lineRegex.find(output)

      val errorLine = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
      val errorColumn = match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0

      NodeErrorInfo(
        errorLine = errorLine,
        errorColumn = errorColumn,
        errorOutput = output.trim()
      )
    } catch (e: Exception) {
      // Node.js not available or other error
      null
    }
  }
}

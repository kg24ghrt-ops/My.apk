package com.skydoves.chatgpt.repo

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.skydoves.chatgpt.data.AppDatabase
import com.skydoves.chatgpt.data.entity.PromptFileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class PromptRepository(private val context: Context) {

  private val appContext = context.applicationContext
  private val db = AppDatabase.getInstance(appContext)
  private val dao = db.promptFileDao()

  fun allFilesFlow() = dao.observeAll()
  suspend fun getById(id: Long) = dao.getById(id)

  // ------------------- IMPORT -------------------
  suspend fun importUriAsFile(uri: Uri, displayName: String?): Long = withContext(Dispatchers.IO) {
    val nameHint = displayName ?: queryFileName(uri) ?: "file_${System.currentTimeMillis()}"
    val safeName = nameHint.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    val originalFile = File(appContext.filesDir, "imported_$safeName")

    appContext.contentResolver.openInputStream(uri)?.use { input ->
      originalFile.outputStream().use { out -> input.copyTo(out) }
    } ?: throw IllegalArgumentException("Cannot open URI: $uri")

    if (safeName.endsWith(".zip", true) || safeName.endsWith(".jar", true)) {
      val zipEntity = PromptFileEntity(
        displayName = originalFile.name,
        filePath = originalFile.absolutePath,
        language = "zip",
        fileSizeBytes = originalFile.length()
      )
      return@withContext dao.insert(zipEntity)
    } else {
      val entity = PromptFileEntity(
        displayName = safeName,
        filePath = originalFile.absolutePath,
        language = guessLanguageFromName(safeName),
        fileSizeBytes = originalFile.length()
      )
      return@withContext dao.insert(entity)
    }
  }

  // ------------------- DELETE -------------------
  suspend fun delete(entity: PromptFileEntity) = withContext(Dispatchers.IO) {
    dao.delete(entity)
    try { File(entity.filePath).deleteRecursively() } catch (_: Exception) {}
  }

  // ------------------- READ CHUNK -------------------
  suspend fun readChunk(entity: PromptFileEntity, offsetBytes: Long, chunkSizeBytes: Int): Pair<String, Long> =
    withContext(Dispatchers.IO) {
      val file = File(entity.filePath)
      if (!file.exists() || file.length() == 0L) return@withContext Pair("", -1L)

      val raf = java.io.RandomAccessFile(file, "r")
      try {
        if (offsetBytes >= raf.length()) return@withContext Pair("", -1L)
        raf.seek(offsetBytes)
        val toRead = minOf(chunkSizeBytes, (raf.length() - offsetBytes).toInt())
        val bytes = ByteArray(toRead)
        val actuallyRead = raf.read(bytes)
        if (actuallyRead <= 0) return@withContext Pair("", -1L)
        val text = String(bytes, 0, actuallyRead, Charsets.UTF_8)
        val nextOffset = offsetBytes + actuallyRead
        Pair(text, if (nextOffset >= raf.length()) -1L else nextOffset)
      } finally {
        try { raf.close() } catch (_: Exception) {}
      }
    }

  // ------------------- PROJECT TREE GENERATOR -------------------
  /**
   * Generate a tree view of a ZIP file with text/code files.
   * This implementation streams entries, enforces limits (max entries, max output bytes)
   * and ensures we never load huge strings into memory unbounded.
   *
   * Returns a textual tree. If truncated due to safety caps, a notice will be appended.
   */
  suspend fun generateProjectTreeFromZip(entity: PromptFileEntity): String = withContext(Dispatchers.IO) {
    val zipFile = File(entity.filePath)
    if (!zipFile.exists()) return@withContext "ZIP file does not exist."

    // Safety caps (tweak if needed)
    val MAX_ENTRIES = 10_000             // max number of entries to scan
    val MAX_OUTPUT_BYTES = 200 * 1024    // 200 KB of output text (approx)
    val MAX_FILE_ENTRY_SIZE = 8 * 1024 * 1024 // skip entry if single file > 8MB (avoid huge single files)

    val out = StringBuilder()
    var outBytes = 0L
    var entriesProcessed = 0

    openZipStream(zipFile).use { zip ->
      val buffer = ByteArray(8 * 1024)
      var entry: ZipEntry? = zip.nextEntry
      while (entry != null && entriesProcessed < MAX_ENTRIES && outBytes < MAX_OUTPUT_BYTES) {
        try {
          if (!entry.isDirectory && looksLikeTextOrCode(entry.name)) {
            val depth = entry.name.count { it == '/' }
            val fileName = entry.name.substringAfterLast('/')

            // header
            val header = "│   ".repeat(depth) + "├─ $fileName\n"
            val headerBytes = header.toByteArray(Charsets.UTF_8)
            if (outBytes + headerBytes.size > MAX_OUTPUT_BYTES) {
              out.append(header.take((MAX_OUTPUT_BYTES - outBytes).toInt()))
              outBytes = MAX_OUTPUT_BYTES
              break
            }
            out.append(header)
            outBytes += headerBytes.size

            // guard: if entry size known and huge, skip content
            val entrySize = entry.size
            if (entrySize != -1L && entrySize > MAX_FILE_ENTRY_SIZE) {
              val skipNote = "│   ".repeat(depth + 1) + "[skipped large file: ${entrySize} bytes]\n"
              val nb = skipNote.toByteArray(Charsets.UTF_8)
              if (outBytes + nb.size > MAX_OUTPUT_BYTES) {
                out.append(skipNote.take((MAX_OUTPUT_BYTES - outBytes).toInt()))
                outBytes = MAX_OUTPUT_BYTES
                break
              }
              out.append(skipNote)
              outBytes += nb.size
            } else {
              // stream entry content but cap bytes appended
              var bytesRead = zip.read(buffer)
              val lineBuilder = StringBuilder()
              while (bytesRead > 0 && outBytes < MAX_OUTPUT_BYTES) {
                val chunk = String(buffer, 0, bytesRead, Charsets.UTF_8)
                lineBuilder.append(chunk)
                bytesRead = zip.read(buffer)
              }
              // append content lines with indent
              val content = lineBuilder.toString()
              val lines = content.lines()
              for (line in lines) {
                val prefixed = "│   ".repeat(depth + 1) + line + "\n"
                val pb = prefixed.toByteArray(Charsets.UTF_8)
                if (outBytes + pb.size > MAX_OUTPUT_BYTES) {
                  val remaining = (MAX_OUTPUT_BYTES - outBytes).toInt()
                  out.append(prefixed.take(remaining))
                  outBytes = MAX_OUTPUT_BYTES
                  break
                } else {
                  out.append(prefixed)
                  outBytes += pb.size
                }
              }
            }
          }
        } catch (e: Exception) {
          // If a single entry fails to read, append a short marker and continue
          val note = "│   [error reading entry ${entry?.name}]\n"
          if (outBytes + note.length <= MAX_OUTPUT_BYTES) {
            out.append(note)
            outBytes += note.toByteArray(Charsets.UTF_8).size
          } else {
            outBytes = MAX_OUTPUT_BYTES
            break
          }
        } finally {
          try { zip.closeEntry() } catch (_: Exception) {}
        }
        entriesProcessed++
        entry = zip.nextEntry
      } // end while entries

      if (entriesProcessed >= MAX_ENTRIES) {
        val note = "\n[truncated: reached max entries limit $MAX_ENTRIES]\n"
        if (outBytes < MAX_OUTPUT_BYTES) out.append(note)
      }
      if (outBytes >= MAX_OUTPUT_BYTES) {
        out.append("\n[truncated: output reached ${MAX_OUTPUT_BYTES} bytes]\n")
      }
    } // zip closed

    out.toString()
  }

  // ---------- HELPERS ----------
  private fun openZipStream(zipFile: File) = ZipInputStream(BufferedInputStream(zipFile.inputStream()))

  private fun queryFileName(uri: Uri): String? {
    var name: String? = null
    val resolver = appContext.contentResolver
    val cursor: Cursor? = try { resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null) } catch (_: Exception) { null }
    cursor?.use {
      if (it.moveToFirst()) {
        val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0) name = it.getString(idx)
      }
    }
    if (name == null) name = uri.lastPathSegment
    return name
  }

  private fun looksLikeTextOrCode(name: String): Boolean {
    val lower = name.lowercase()
    val textExt = listOf(
      ".kt", ".java", ".xml", ".json", ".md", ".py", ".js", ".ts",
      ".c", ".cpp", ".h", ".txt", ".gradle", ".properties", ".yml", ".yaml", ".html", ".css", ".rb", ".go", ".rs", ".swift", ".php"
    )
    return textExt.any { lower.endsWith(it) }
  }

  private fun guessLanguageFromName(name: String): String? = when {
    name.endsWith(".kt", true) -> "kotlin"
    name.endsWith(".java", true) -> "java"
    name.endsWith(".xml", true) -> "xml"
    name.endsWith(".json", true) -> "json"
    name.endsWith(".md", true) -> "markdown"
    name.endsWith(".py", true) -> "python"
    name.endsWith(".js", true) -> "javascript"
    name.endsWith(".c", true) -> "c"
    name.endsWith(".cpp", true) -> "cpp"
    else -> null
  }
}
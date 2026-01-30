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

    // For zip/jar we keep the original as an entity and allow separate project-tree generation.
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
        val next = offsetBytes + actuallyRead
        val nextOffset = if (next >= raf.length()) -1L else next
        Pair(text, nextOffset)
      } finally {
        try { raf.close() } catch (_: Exception) {}
      }
    }

  // ------------------- PROJECT TREE GENERATOR -------------------
  /**
   * Generate a tree view of a ZIP file with text/code files.
   * Safety: limit per-file included bytes and total bytes to avoid OOM / long processing.
   *
   * Returns a large string containing tree structure and truncated file contents.
   */
  suspend fun generateProjectTreeFromZip(entity: PromptFileEntity): String = withContext(Dispatchers.IO) {
    val zipFile = File(entity.filePath)
    if (!zipFile.exists()) return@withContext "ZIP file does not exist."

    // Safety caps
    val MAX_TOTAL_BYTES: Long = 10L * 1024L * 1024L // at most include 10 MB total across files
    val MAX_FILE_BYTES: Int = 128 * 1024 // at most include 128 KB per file
    val MAX_ENTRIES = 5000

    val treeBuilder = StringBuilder()
    var totalIncludedBytes: Long = 0L
    var entriesProcessed = 0

    try {
      openZipStream(zipFile).use { zip ->
        val buffer = ByteArray(8 * 1024)
        var entry: ZipEntry? = zip.nextEntry

        while (entry != null && entriesProcessed < MAX_ENTRIES && totalIncludedBytes < MAX_TOTAL_BYTES) {
          try {
            if (!entry.isDirectory && looksLikeTextOrCode(entry.name)) {
              val depth = entry.name.count { it == '/' }
              treeBuilder.append("│   ".repeat(depth.coerceAtLeast(0)))
              val fileName = entry.name.substringAfterLast('/')
              treeBuilder.append("├─ $fileName\n")

              // Read up to MAX_FILE_BYTES from the entry
              val fileBuffer = StringBuilder()
              var bytesReadForThisFile = 0
              var bytesRead = zip.read(buffer)
              while (bytesRead > 0 && bytesReadForThisFile < MAX_FILE_BYTES && totalIncludedBytes < MAX_TOTAL_BYTES) {
                val toAppend = if (bytesReadForThisFile + bytesRead > MAX_FILE_BYTES) {
                  // partial read: append only remaining allowed bytes
                  val allowed = MAX_FILE_BYTES - bytesReadForThisFile
                  String(buffer, 0, allowed, Charsets.UTF_8)
                } else {
                  String(buffer, 0, bytesRead, Charsets.UTF_8)
                }

                fileBuffer.append(toAppend)
                val actuallyCounted = minOf(bytesRead, MAX_FILE_BYTES - bytesReadForThisFile)
                bytesReadForThisFile += actuallyCounted
                totalIncludedBytes += actuallyCounted.toLong()

                if (bytesReadForThisFile >= MAX_FILE_BYTES || totalIncludedBytes >= MAX_TOTAL_BYTES) {
                  // stop reading more for safety
                  break
                }
                bytesRead = zip.read(buffer)
              }

              // append file content indented
              fileBuffer.lines().forEach { line ->
                treeBuilder.append("│   ".repeat(depth + 1))
                treeBuilder.append(line).append("\n")
              }

              if (bytesReadForThisFile >= MAX_FILE_BYTES) {
                treeBuilder.append("│   ".repeat(depth + 1)).append("[...content truncated: per-file limit reached]\n")
              }
              if (totalIncludedBytes >= MAX_TOTAL_BYTES) {
                treeBuilder.append("\n[...total inclusion cap reached, further files omitted]\n")
              }
            } else {
              // entry is binary or directory: drain it safely
              val drainBuf = ByteArray(8 * 1024)
              while (zip.read(drainBuf) > 0) { /* drain */ }
            }
          } finally {
            try { zip.closeEntry() } catch (_: Exception) {}
          }

          entriesProcessed++
          entry = zip.nextEntry
        } // end while entries
      } // zip closed
    } catch (e: Exception) {
      // propagate a helpful message (your ViewModel will capture/report)
      return@withContext "Failed to read ZIP: ${e.message ?: e.javaClass.simpleName}"
    }

    if (treeBuilder.isEmpty()) {
      return@withContext "No text/code files found in ZIP."
    }
    treeBuilder.toString()
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
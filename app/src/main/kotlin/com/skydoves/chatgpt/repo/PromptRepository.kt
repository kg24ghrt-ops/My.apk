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
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class PromptRepository(private val context: Context) {

  private val appContext = context.applicationContext
  private val db = AppDatabase.getInstance(appContext)
  private val dao = db.promptFileDao()

  fun allFilesFlow() = dao.observeAll()

  suspend fun getById(id: Long) = dao.getById(id)

  /**
   * Import a Uri into internal storage. If it's a ZIP, extract entries and insert entities
   * for extracted code/text files. Returns the inserted id of the first meaningful file,
   * or the inserted id of the original file when not a ZIP or extraction produced nothing.
   */
  suspend fun importUriAsFile(uri: Uri, displayName: String?): Long = withContext(Dispatchers.IO) {
    val nameHint = displayName ?: queryFileName(uri) ?: "file_${System.currentTimeMillis()}"
    val safeName = nameHint.replace(Regex("[^a-zA-Z0-9._-]"), "_")

    // copy original to internal storage
    val originalFile = File(appContext.filesDir, "imported_$safeName")
    appContext.contentResolver.openInputStream(uri)?.use { input ->
      originalFile.outputStream().use { out -> input.copyTo(out) }
    } ?: throw IllegalArgumentException("Cannot open URI: $uri")

    // if it's zip-like, try to extract
    if (safeName.endsWith(".zip", ignoreCase = true) || safeName.endsWith(".jar", ignoreCase = true)) {
      val extractedDir = File(appContext.filesDir, "imported_${safeName}_extracted")
      if (!extractedDir.exists()) extractedDir.mkdirs()

      val extractedInsertedIds = mutableListOf<Long>()
      openZipStream(originalFile).use { zip ->
        var entry: ZipEntry? = zip.nextEntry
        var entriesCount = 0
        val MAX_ENTRIES = 2000 // guard against zip bombs (configurable)
        val MAX_FILE_BYTES = 200 * 1024 * 1024L // 200 MB per extracted file limit (configurable)

        while (entry != null && entriesCount < MAX_ENTRIES) {
          if (!entry.isDirectory) {
            val entryName = entry.name.substringAfterLast('/') // keep only file name
            val outFile = File(extractedDir, entryName)
            // create parent dirs
            outFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

            // stream entry to file (do not allocate whole entry in memory)
            FileOutputStream(outFile).use { fos ->
              val buffer = ByteArray(8 * 1024)
              var read: Int
              var written: Long = 0
              while (zip.read(buffer).also { read = it } > 0) {
                fos.write(buffer, 0, read)
                written += read
                if (written > MAX_FILE_BYTES) {
                  // abort this entry if it's too big
                  fos.flush()
                  fos.close()
                  outFile.delete()
                  break
                }
              }
            }

            // if file exists and is small enough and looks like text/code, insert
            if (outFile.exists() && outFile.length() in 1..MAX_FILE_BYTES) {
              if (looksLikeTextOrCode(outFile.name)) {
                val entity = PromptFileEntity(
                  displayName = outFile.name,
                  filePath = outFile.absolutePath,
                  language = guessLanguageFromName(outFile.name),
                  fileSizeBytes = outFile.length()
                )
                val id = dao.insert(entity)
                extractedInsertedIds.add(id)
              }
            }
            entriesCount++
          } else {
            // if directory, ensure it's created
            val dir = File(extractedDir, entry.name)
            if (!dir.exists()) dir.mkdirs()
          }
          zip.closeEntry()
          entry = zip.nextEntry
        }
      }

      // if we extracted at least one file, return first id
      if (extractedInsertedIds.isNotEmpty()) {
        return@withContext extractedInsertedIds.first()
      }

      // fallback: if nothing extracted/inserted, insert the zip file as a single entity
      val zipEntity = PromptFileEntity(
        displayName = originalFile.name,
        filePath = originalFile.absolutePath,
        language = "zip",
        fileSizeBytes = originalFile.length()
      )
      return@withContext dao.insert(zipEntity)
    } else {
      // non-zip: insert single entity
      val entity = PromptFileEntity(
        displayName = safeName,
        filePath = originalFile.absolutePath,
        language = guessLanguageFromName(safeName),
        fileSizeBytes = originalFile.length()
      )
      return@withContext dao.insert(entity)
    }
  }

  suspend fun delete(entity: PromptFileEntity) = withContext(Dispatchers.IO) {
    dao.delete(entity)
    try { File(entity.filePath).deleteRecursively() } catch (_: Exception) {}
  }

  /**
   * Read a chunk safely (UTF-8) from a file. Returns Pair(text, nextOffset) where nextOffset == -1L for EOF.
   */
  suspend fun readChunk(entity: PromptFileEntity, offsetBytes: Long, chunkSizeBytes: Int): Pair<String, Long> {
    return withContext(Dispatchers.IO) {
      val file = File(entity.filePath)
      if (!file.exists() || file.length() == 0L) return@withContext Pair("", -1L)

      // use RandomAccessFile for byte-accurate seeking
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
  }

  // ---------- helpers ----------

  private fun openZipStream(zipFile: File): ZipInputStream {
    val fis = zipFile.inputStream()
    val bis = BufferedInputStream(fis)
    return ZipInputStream(bis)
  }

  /**
   * Query display name (filename) for a content Uri if possible.
   */
  private fun queryFileName(uri: Uri): String? {
    var name: String? = null
    val resolver = appContext.contentResolver
    val cursor: Cursor? = try {
      resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    } catch (_: Exception) {
      null
    }
    cursor?.use {
      if (it.moveToFirst()) {
        name = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
      }
    }
    // fallback to last path segment
    if (name == null) name = uri.lastPathSegment
    return name
  }

  private fun looksLikeTextOrCode(name: String): Boolean {
    val lower = name.lowercase()
    val textExt = listOf(
      ".kt", ".java", ".xml", ".json", ".md", ".py", ".js", ".ts",
      ".c", ".cpp", ".h", ".txt", ".gradle", ".properties", ".yml", ".yaml", ".html", ".css", ".rb", ".go", ".rs"
    )
    return textExt.any { lower.endsWith(it) }
  }

  private fun guessLanguageFromName(name: String): String? =
    when {
      name.endsWith(".kt", ignoreCase = true) -> "kotlin"
      name.endsWith(".java", ignoreCase = true) -> "java"
      name.endsWith(".xml", ignoreCase = true) -> "xml"
      name.endsWith(".json", ignoreCase = true) -> "json"
      name.endsWith(".md", ignoreCase = true) -> "markdown"
      name.endsWith(".py", ignoreCase = true) -> "python"
      name.endsWith(".js", ignoreCase = true) -> "javascript"
      name.endsWith(".c", ignoreCase = true) -> "c"
      name.endsWith(".cpp", ignoreCase = true) -> "cpp"
      else -> null
    }
}
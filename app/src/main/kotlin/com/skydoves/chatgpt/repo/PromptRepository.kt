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

  suspend fun importUriAsFile(uri: Uri, displayName: String?): Long = withContext(Dispatchers.IO) {
    val nameHint = displayName ?: queryFileName(uri) ?: "file_${System.currentTimeMillis()}"
    val safeName = nameHint.replace(Regex("[^a-zA-Z0-9._-]"), "_")

    val originalFile = File(appContext.filesDir, "imported_$safeName")
    appContext.contentResolver.openInputStream(uri)?.use { input ->
      originalFile.outputStream().use { out -> input.copyTo(out) }
    } ?: throw IllegalArgumentException("Cannot open URI: $uri")

    if (safeName.endsWith(".zip", true) || safeName.endsWith(".jar", true)) {
      val mergedFile = File(appContext.filesDir, "imported_${safeName}_merged.txt")
      if (mergedFile.exists()) mergedFile.delete()

      var mergedAnything = false
      val MAX_MERGED_BYTES = 400L * 1024L * 1024L
      var mergedBytes: Long = 0

      openZipStream(originalFile).use { zip ->
        var entry: ZipEntry? = zip.nextEntry
        val MAX_ENTRIES = 5000
        var entriesProcessed = 0

        FileOutputStream(mergedFile, false).use { mergedOut ->
          val buffer = ByteArray(8 * 1024)
          while (entry != null && entriesProcessed < MAX_ENTRIES) {
            if (!entry.isDirectory) {
              val entryName = entry.name.substringAfterLast('/')
              if (looksLikeTextOrCode(entryName)) {
                val header = "\n\n--- FILE: $entryName ---\n\n"
                mergedOut.write(header.toByteArray(Charsets.UTF_8))
                mergedBytes += header.toByteArray(Charsets.UTF_8).size

                var read: Int
                while (zip.read(buffer).also { read = it } > 0) {
                  mergedOut.write(buffer, 0, read)
                  mergedBytes += read
                  if (mergedBytes > MAX_MERGED_BYTES) break
                }
                mergedOut.flush()
                mergedAnything = true
              } else {
                val skipBuffer = ByteArray(8 * 1024)
                while (zip.read(skipBuffer).also { read = it } > 0) {}
              }
            }
            zip.closeEntry()
            entry = zip.nextEntry
            entriesProcessed++
            if (mergedBytes > MAX_MERGED_BYTES) break
          }
        }
      }

      if (mergedAnything && mergedFile.exists() && mergedFile.length() > 0L) {
        val entity = PromptFileEntity(
          displayName = displayName ?: "${safeName}_merged.txt",
          filePath = mergedFile.absolutePath,
          language = guessLanguageFromName(mergedFile.name),
          fileSizeBytes = mergedFile.length()
        )
        return@withContext dao.insert(entity)
      }

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

  suspend fun delete(entity: PromptFileEntity) = withContext(Dispatchers.IO) {
    dao.delete(entity)
    try { File(entity.filePath).deleteRecursively() } catch (_: Exception) {}
  }

  suspend fun readChunk(entity: PromptFileEntity, offsetBytes: Long, chunkSizeBytes: Int): Pair<String, Long> {
    return withContext(Dispatchers.IO) {
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
  }

  private fun openZipStream(zipFile: File): ZipInputStream {
    val fis = zipFile.inputStream()
    val bis = BufferedInputStream(fis)
    return ZipInputStream(bis)
  }

  private fun queryFileName(uri: Uri): String? {
    var name: String? = null
    val resolver = appContext.contentResolver
    val cursor: Cursor? = try { resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null) } catch (_: Exception) { null }
    cursor?.use { if (it.moveToFirst()) {
      val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      if (idx >= 0) name = it.getString(idx)
    }}
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
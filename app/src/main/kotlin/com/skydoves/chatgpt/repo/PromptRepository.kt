package com.skydoves.chatgpt.repo

import android.content.Context
import android.net.Uri
import com.skydoves.chatgpt.data.AppDatabase
import com.skydoves.chatgpt.data.entity.PromptFileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class PromptRepository(private val context: Context) {

  private val appContext = context.applicationContext
  private val db = AppDatabase.getInstance(appContext)
  private val dao = db.promptFileDao()

  // Keep old name so ViewModel stays compatible
  fun allFilesFlow() = dao.getAllFlow()

  suspend fun getById(id: Long) = dao.getById(id)

  /**
   * Import a file URI into internal storage and store metadata only.
   * Returns the inserted row id.
   */
  suspend fun importUriAsFile(
    uri: Uri,
    displayName: String?
  ): Long = withContext(Dispatchers.IO) {

    val safeName = (displayName ?: "file_${System.currentTimeMillis()}")
      .replace(Regex("[^a-zA-Z0-9._-]"), "_")

    val outFile = File(appContext.filesDir, "imported_$safeName")

    appContext.contentResolver.openInputStream(uri)?.use { input ->
      outFile.outputStream().use { output ->
        input.copyTo(output)
      }
    } ?: throw IllegalArgumentException("Cannot open URI: $uri")

    val entity = PromptFileEntity(
      displayName = displayName ?: safeName,
      filePath = outFile.absolutePath,
      language = guessLanguageFromName(safeName),
      fileSizeBytes = outFile.length()
    )

    dao.insert(entity)
  }

  suspend fun delete(entity: PromptFileEntity) = withContext(Dispatchers.IO) {
    dao.delete(entity)
    runCatching { File(entity.filePath).delete() }
  }

  /**
   * Read a UTF-8 safe chunk of a file using RandomAccessFile semantics.
   * Returns Pair(text, nextOffset) where nextOffset == -1L indicates EOF.
   */
  suspend fun readChunk(entity: PromptFileEntity, offsetBytes: Long, chunkSizeBytes: Int): Pair<String, Long> {
    return withContext(Dispatchers.IO) {
      val file = File(entity.filePath)
      if (!file.exists()) return@withContext Pair("", -1L)

      val raf = java.io.RandomAccessFile(file, "r")
      try {
        if (offsetBytes >= raf.length()) return@withContext Pair("", -1L)
        raf.seek(offsetBytes)
        val toRead = minOf(chunkSizeBytes, (raf.length() - offsetBytes).toInt())
        val bytes = ByteArray(toRead)
        val actuallyRead = raf.read(bytes)
        if (actuallyRead <= 0) return@withContext Pair("", -1L)
        // decode with UTF-8
        val text = String(bytes, 0, actuallyRead, StandardCharsets.UTF_8)
        val next = offsetBytes + actuallyRead
        val nextOffset = if (next >= raf.length()) -1L else next
        Pair(text, nextOffset)
      } finally {
        try { raf.close() } catch (_: Exception) {}
      }
    }
  }

  private fun guessLanguageFromName(name: String): String? =
    when {
      name.endsWith(".kt") -> "kotlin"
      name.endsWith(".java") -> "java"
      name.endsWith(".xml") -> "xml"
      name.endsWith(".json") -> "json"
      name.endsWith(".md") -> "markdown"
      name.endsWith(".py") -> "python"
      else -> null
    }
}
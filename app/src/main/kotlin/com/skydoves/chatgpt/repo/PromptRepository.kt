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

class PromptRepository(context: Context) {

  private val appContext = context.applicationContext
  private val db = AppDatabase.getInstance(appContext)
  private val dao = db.promptFileDao()

  fun observeAllFiles() = dao.observeAll()

  suspend fun getById(id: Long) = dao.getById(id)

  /**
   * Import a file URI into internal storage and store metadata only.
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
    dao.deleteById(entity.id)
    runCatching { File(entity.filePath).delete() }
  }

  /**
   * Reads a UTF-8 safe chunk of a file.
   * Never loads full file into memory.
   */
  suspend fun readChunk(
    entity: PromptFileEntity,
    offsetBytes: Long,
    chunkSizeBytes: Int
  ): Pair<String, Long> = withContext(Dispatchers.IO) {

    val file = File(entity.filePath)
    if (!file.exists()) return@withContext "" to -1L

    val raf = file.inputStream()
    try {
      raf.skip(offsetBytes)

      val reader = InputStreamReader(raf, StandardCharsets.UTF_8)
      val buffer = CharArray(chunkSizeBytes)
      val read = reader.read(buffer)

      if (read <= 0) return@withContext "" to -1L

      val nextOffset = offsetBytes + read
      buffer.concatToString(0, read) to
        if (nextOffset >= file.length()) -1L else nextOffset
    } finally {
      try { raf.close() } catch (_: Exception) {}
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
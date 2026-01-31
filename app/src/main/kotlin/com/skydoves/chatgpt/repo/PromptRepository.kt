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

    val entity = PromptFileEntity(
      displayName = safeName,
      filePath = originalFile.absolutePath,
      language = if (safeName.endsWith(".zip", true)) "zip" else guessLanguageFromName(safeName),
      fileSizeBytes = originalFile.length()
    )
    return@withContext dao.insert(entity)
  }

  // ------------------- AI CONTEXT WRAPPER -------------------

  /**
   * Bundles the project tree and file content into a professional AI Prompt.
   */
  suspend fun bundleContextForAI(entity: PromptFileEntity): String = withContext(Dispatchers.IO) {
    dao.updateLastAccessed(entity.id)
    
    val tree = entity.lastKnownTree ?: generateProjectTreeFromZip(entity)
    val summary = entity.summary ?: "No summary provided."

    buildString {
      append("### PROJECT CONTEXT WRAPPER\n")
      append("**File/Project Name:** ${entity.displayName}\n")
      append("**Summary:** $summary\n\n")
      append("#### PROJECT STRUCTURE\n")
      append("```text\n")
      append(tree)
      append("\n```\n\n")
      append("#### TASK\n")
      append("Please analyze the project structure above and provide insights or assist with the requested code changes.")
    }
  }

  // ------------------- PROJECT TREE GENERATOR -------------------
  suspend fun generateProjectTreeFromZip(entity: PromptFileEntity): String = withContext(Dispatchers.IO) {
    val zipFile = File(entity.filePath)
    if (!zipFile.exists()) return@withContext "ZIP file does not exist."

    val MAX_TOTAL_CHARS = 100_000
    val MAX_FILE_CHARS = 4_000
    val treeBuilder = StringBuilder()

    try {
      ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zip ->
        val buffer = ByteArray(4096)
        var entry: ZipEntry? = zip.nextEntry
        while (entry != null && treeBuilder.length < MAX_TOTAL_CHARS) {
          if (!entry.isDirectory && looksLikeTextOrCode(entry.name)) {
            val depth = entry.name.count { it == '/' }
            val indent = "│   ".repeat(depth)
            treeBuilder.append(indent).append("├─ ${entry.name.substringAfterLast('/')}\n")
            
            val contentIndent = "│   ".repeat(depth + 1)
            var bytesReadTotal = 0
            var bytesRead = zip.read(buffer)
            while (bytesRead > 0 && bytesReadTotal < MAX_FILE_CHARS) {
              val chunk = String(buffer, 0, bytesRead, Charsets.UTF_8)
              chunk.forEach { 
                treeBuilder.append(it)
                if (it == '\n') treeBuilder.append(contentIndent)
              }
              bytesReadTotal += bytesRead
              bytesRead = zip.read(buffer)
            }
            treeBuilder.append("\n")
          }
          zip.closeEntry()
          entry = zip.nextEntry
        }
      }
      
      val finalTree = treeBuilder.toString()
      // PERSIST THE TREE so we don't have to re-zip next time
      dao.updateTree(entity.id, finalTree)
      return@withContext finalTree
      
    } catch (e: Exception) {
      return@withContext "Error: ${e.message}"
    }
  }

  // ------------------- HELPERS & CHUNKS -------------------
  
  suspend fun delete(entity: PromptFileEntity) = withContext(Dispatchers.IO) {
    dao.delete(entity)
    File(entity.filePath).delete()
  }

  suspend fun readChunk(entity: PromptFileEntity, offsetBytes: Long, chunkSizeBytes: Int): Pair<String, Long> =
    withContext(Dispatchers.IO) {
      val file = File(entity.filePath)
      if (!file.exists()) return@withContext "" to -1L
      java.io.RandomAccessFile(file, "r").use { raf ->
        raf.seek(offsetBytes)
        val bytes = ByteArray(minOf(chunkSizeBytes, (raf.length() - offsetBytes).toInt()))
        val read = raf.read(bytes)
        val text = String(bytes, 0, read, Charsets.UTF_8)
        val next = if (offsetBytes + read >= raf.length()) -1L else offsetBytes + read
        text to next
      }
    }

  private fun queryFileName(uri: Uri): String? {
    val cursor = appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    return cursor?.use {
      if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
    }
  }

  private fun looksLikeTextOrCode(name: String): Boolean {
    val ext = listOf(".kt", ".java", ".xml", ".json", ".md", ".txt", ".gradle", ".properties")
    return ext.any { name.lowercase().endsWith(it) }
  }

  private fun guessLanguageFromName(name: String): String? = when {
    name.endsWith(".kt") -> "kotlin"
    name.endsWith(".java") -> "java"
    else -> null
  }
}

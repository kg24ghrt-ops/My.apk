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

  // REUSABLE BUFFER: Prevents memory thrashing
  private val sharedBuffer = ByteArray(8192)

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
  suspend fun bundleContextForAI(entity: PromptFileEntity): String = withContext(Dispatchers.IO) {
    dao.updateLastAccessed(entity.id)
    
    val tree = entity.lastKnownTree ?: generateProjectTreeFromZip(entity)
    val summary = entity.summary ?: "No summary provided."

    // Pre-allocate space for a large prompt to avoid memory re-allocations
    StringBuilder(tree.length + 500).apply {
      append("### PROJECT CONTEXT WRAPPER\n")
      append("**File/Project Name:** ${entity.displayName}\n")
      append("**Summary:** $summary\n\n")
      append("#### PROJECT STRUCTURE\n")
      append("```text\n")
      append(tree)
      append("\n```\n\n")
      append("#### TASK\n")
      append("Please analyze the project structure above and provide insights.")
    }.toString()
  }

  // ------------------- OPTIMIZED PROJECT TREE GENERATOR -------------------
  suspend fun generateProjectTreeFromZip(entity: PromptFileEntity): String = withContext(Dispatchers.IO) {
    val zipFile = File(entity.filePath)
    if (!zipFile.exists()) return@withContext "ZIP missing."

    val MAX_TOTAL_CHARS = 120_000
    val MAX_FILE_CHARS = 5_000
    // Pre-allocate 64KB for the builder
    val treeBuilder = StringBuilder(65536)

    try {
      // Use 32KB buffer for the input stream to minimize disk seek overhead
      ZipInputStream(BufferedInputStream(zipFile.inputStream(), 32768)).use { zip ->
        var entry: ZipEntry? = zip.nextEntry
        while (entry != null && treeBuilder.length < MAX_TOTAL_CHARS) {
          if (!entry.isDirectory && looksLikeTextOrCode(entry.name)) {
            val depth = entry.name.count { it == '/' }
            val indent = "│   ".repeat(depth)
            treeBuilder.append(indent).append("├─ ${entry.name.substringAfterLast('/')}\n")
            
            val contentIndent = "│   ".repeat(depth + 1)
            var charsReadForFile = 0
            
            var bytesRead = zip.read(sharedBuffer)
            while (bytesRead > 0 && charsReadForFile < MAX_FILE_CHARS) {
              val chunk = String(sharedBuffer, 0, bytesRead, Charsets.UTF_8)
              
              // Optimized line-by-line indenting
              chunk.split('\n').forEach { line ->
                if (treeBuilder.length < MAX_TOTAL_CHARS) {
                  treeBuilder.append(contentIndent).append(line).append("\n")
                }
              }

              charsReadForFile += bytesRead
              if (treeBuilder.length >= MAX_TOTAL_CHARS) break
              bytesRead = zip.read(sharedBuffer)
            }
          }
          zip.closeEntry()
          entry = zip.nextEntry
        }
      }
      
      val finalTree = treeBuilder.toString()
      dao.updateTree(entity.id, finalTree)
      return@withContext finalTree
      
    } catch (e: Exception) {
      return@withContext "Error reading tree: ${e.localizedMessage}"
    }
  }

  // ------------------- OPTIMIZED CHUNK READING -------------------
  suspend fun readChunk(entity: PromptFileEntity, offsetBytes: Long, chunkSizeBytes: Int): Pair<String, Long> =
    withContext(Dispatchers.IO) {
      val file = File(entity.filePath)
      if (!file.exists()) return@withContext "" to -1L
      
      java.io.RandomAccessFile(file, "r").use { raf ->
        if (offsetBytes >= raf.length()) return@withContext "" to -1L
        
        raf.seek(offsetBytes)
        val actualToRead = minOf(chunkSizeBytes, (raf.length() - offsetBytes).toInt())
        val localBuffer = if (actualToRead <= sharedBuffer.size) sharedBuffer else ByteArray(actualToRead)
        
        val read = raf.read(localBuffer, 0, actualToRead)
        val text = String(localBuffer, 0, read, Charsets.UTF_8)
        
        val next = if (offsetBytes + read >= raf.length()) -1L else offsetBytes + read
        text to next
      }
    }

  suspend fun delete(entity: PromptFileEntity) = withContext(Dispatchers.IO) {
    dao.delete(entity)
    File(entity.filePath).delete()
  }

  private fun queryFileName(uri: Uri): String? {
    return try {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
        }
    } catch (e: Exception) { null } ?: uri.lastPathSegment
  }

  private fun looksLikeTextOrCode(name: String): Boolean {
    val lower = name.lowercase()
    return listOf(".kt", ".java", ".xml", ".json", ".md", ".txt", ".gradle", ".properties", ".yml", ".yaml", ".js", ".ts", ".html", ".css").any { lower.endsWith(it) }
  }

  private fun guessLanguageFromName(name: String): String? = when {
    name.endsWith(".kt") -> "kotlin"
    name.endsWith(".java") -> "java"
    name.endsWith(".xml") -> "xml"
    name.endsWith(".json") -> "json"
    name.endsWith(".md") -> "markdown"
    else -> null
  }
}

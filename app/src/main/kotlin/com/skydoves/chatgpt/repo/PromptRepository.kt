package com.skydoves.chatgpt.repo

import android.content.Context
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
  private val sharedBuffer = ByteArray(8192)

  // --- SELECTIVE PACKAGING: THE IGNORE ENGINE ---
  private val IGNORED_PATHS = listOf(
    "/node_modules/", "/.git/", "/build/", "/.gradle/", "/.idea/", 
    "/target/", "/bin/", "/out/", "/vendor/", "/.next/", "/.venv/"
  )
  
  private val IGNORED_FILES = listOf(
    "package-lock.json", "yarn.lock", "gradlew", "gradlew.bat", 
    ".DS_Store", "LICENSE", "gradle-wrapper.properties"
  )

  fun allFilesFlow() = dao.observeAll()
  suspend fun getById(id: Long) = dao.getById(id)

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

  suspend fun bundleContextForAI(entity: PromptFileEntity): String = withContext(Dispatchers.IO) {
    dao.updateLastAccessed(entity.id)
    val tree = entity.lastKnownTree ?: generateProjectTreeFromZip(entity)
    
    StringBuilder().apply {
      append("### PROJECT CONTEXT: ${entity.displayName}\n")
      append("#### DIRECTORY STRUCTURE (FILTERED)\n")
      append("```text\n$tree\n```\n")
      append("#### INSTRUCTIONS\n")
      append("Analyze the provided structure and logic. Ignore system-generated files.")
    }.toString()
  }

  // --- SELECTIVE TREE GENERATION ---
  suspend fun generateProjectTreeFromZip(entity: PromptFileEntity): String = withContext(Dispatchers.IO) {
    val zipFile = File(entity.filePath)
    if (!zipFile.exists()) return@withContext "ZIP missing."

    val MAX_TOTAL_CHARS = 120_000
    val MAX_FILE_CHARS = 3_000 // Slightly smaller per-file limit to fit more files
    val treeBuilder = StringBuilder(65536)

    try {
      ZipInputStream(BufferedInputStream(zipFile.inputStream(), 32768)).use { zip ->
        var entry: ZipEntry? = zip.nextEntry
        while (entry != null && treeBuilder.length < MAX_TOTAL_CHARS) {
          
          // CRITICAL: Selective Filtering Logic
          if (shouldIgnore(entry.name)) {
            zip.closeEntry()
            entry = zip.nextEntry
            continue
          }

          if (!entry.isDirectory && looksLikeTextOrCode(entry.name)) {
            val depth = entry.name.count { it == '/' }
            val indent = "│   ".repeat(depth)
            val fileName = entry.name.substringAfterLast('/')
            
            treeBuilder.append(indent).append("├── $fileName\n")
            
            val contentIndent = "│   ".repeat(depth + 1)
            var charsReadForFile = 0
            
            var bytesRead = zip.read(sharedBuffer)
            while (bytesRead > 0 && charsReadForFile < MAX_FILE_CHARS) {
              val chunk = String(sharedBuffer, 0, bytesRead, Charsets.UTF_8)
              
              chunk.split('\n').forEach { line ->
                if (treeBuilder.length < MAX_TOTAL_CHARS && line.isNotBlank()) {
                  treeBuilder.append(contentIndent).append(line.trim()).append("\n")
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
      return@withContext "Error packaging: ${e.localizedMessage}"
    }
  }

  // --- FILTERING LOGIC ---
  private fun shouldIgnore(path: String): Boolean {
    val normalized = "/$path".lowercase()
    val fileName = path.substringAfterLast('/').lowercase()
    
    // Check if path contains ignored directories
    if (IGNORED_PATHS.any { normalized.contains(it) }) return true
    
    // Check if filename is in blacklist
    if (IGNORED_FILES.any { fileName == it }) return true
    
    // Ignore hidden files (starting with dot) except common config files
    if (fileName.startsWith(".") && fileName != ".gitignore" && fileName != ".env") return true
    
    return false
  }

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
        text to (if (offsetBytes + read >= raf.length()) -1L else offsetBytes + read)
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
    return listOf(".kt", ".java", ".xml", ".json", ".md", ".txt", ".gradle", ".properties", ".yml", ".yaml", ".js", ".ts", ".html", ".css", ".py", ".cpp", ".h").any { lower.endsWith(it) }
  }

  private fun guessLanguageFromName(name: String): String? = when {
    name.endsWith(".kt") -> "kotlin"
    name.endsWith(".java") -> "java"
    name.endsWith(".py") -> "python"
    name.endsWith(".xml") -> "xml"
    name.endsWith(".json") -> "json"
    name.endsWith(".md") -> "markdown"
    else -> null
  }
}

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
import java.nio.charset.MalformedInputException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class PromptRepository(private val context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    private val dao = db.promptFileDao()
    private val sharedBuffer = ByteArray(8192)

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

    // --- UPDATED: SELECTIVE BUNDLING LOGIC ---
    suspend fun bundleContextForAI(
        entity: PromptFileEntity,
        includeTree: Boolean,
        includePreview: Boolean,
        includeSummary: Boolean,
        includeInstructions: Boolean
    ): String = withContext(Dispatchers.IO) {
        dao.updateLastAccessed(entity.id)
        
        val tree = if (includeTree) (entity.lastKnownTree ?: generateProjectTreeFromZip(entity)) else null
        val summary = if (includeSummary) (entity.summary ?: "No summary available.") else null

        StringBuilder().apply {
            append("### DEV-AI CONTEXT: ${entity.displayName}\n")
            
            if (includeSummary) {
                append("#### SUMMARY\n$summary\n\n")
            }

            if (includeTree && !tree.isNullOrBlank()) {
                append("#### DIRECTORY STRUCTURE\n")
                append("```text\n$tree\n```\n\n")
            }

            if (includePreview) {
                append("#### SOURCE PREVIEW\n")
                append("(Significant code snippets from the main project files are included below)\n\n")
                // Logic to append previews would go here or be part of the tree generator
            }

            if (includeInstructions) {
                append("#### TASK & INSTRUCTIONS\n")
                append("1. Analyze the provided project architecture.\n")
                append("2. Identify core logic and potential edge cases.\n")
                append("3. Provide concise, expert-level implementation advice.\n")
            }
        }.toString()
    }

    // --- UPDATED: BINARY-AWARE TREE GENERATION ---
    suspend fun generateProjectTreeFromZip(entity: PromptFileEntity): String = withContext(Dispatchers.IO) {
        val zipFile = File(entity.filePath)
        if (!zipFile.exists()) return@withContext "ZIP missing."

        val MAX_TOTAL_CHARS = 120_000
        val MAX_FILE_CHARS = 3_000 
        val treeBuilder = StringBuilder(65536)

        try {
            ZipInputStream(BufferedInputStream(zipFile.inputStream(), 32768)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null && treeBuilder.length < MAX_TOTAL_CHARS) {
                    
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
                        
                        // Buffer content to check for binary signatures
                        val fileBytes = mutableListOf<Byte>()
                        var bytesRead = zip.read(sharedBuffer)
                        
                        while (bytesRead > 0 && charsReadForFile < MAX_FILE_CHARS) {
                            // Check for null bytes (Binary indicator)
                            if (sharedBuffer.take(bytesRead).any { it == 0.toByte() }) {
                                treeBuilder.append(contentIndent).append("[Binary Content Skipped]\n")
                                break
                            }

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

    private fun shouldIgnore(path: String): Boolean {
        val normalized = "/$path".lowercase()
        val fileName = path.substringAfterLast('/').lowercase()
        if (IGNORED_PATHS.any { normalized.contains(it) }) return true
        if (IGNORED_FILES.any { fileName == it }) return true
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
                val localBuffer = ByteArray(actualToRead)
                val read = raf.read(localBuffer, 0, actualToRead)
                
                // VALIDATION: Ensure we don't return binary gibberish to the UI
                val text = try {
                    if (localBuffer.take(read).any { it == 0.toByte() }) {
                        "[Binary File]"
                    } else {
                        String(localBuffer, 0, read, Charsets.UTF_8)
                    }
                } catch (e: Exception) {
                    "[Decode Error]"
                }
                
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
        val extensions = listOf(".kt", ".java", ".xml", ".json", ".md", ".txt", ".gradle", ".properties", ".yml", ".yaml", ".js", ".ts", ".html", ".css", ".py", ".cpp", ".h")
        return extensions.any { lower.endsWith(it) }
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

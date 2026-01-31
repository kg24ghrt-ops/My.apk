package com.skydoves.chatgpt.repo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.skydoves.chatgpt.data.AppDatabase
import com.skydoves.chatgpt.data.entity.PromptFileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class PromptRepository(context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    private val dao = db.promptFileDao()
    
    private val IO_BUFFER_SIZE = 32768 
    
    // Using HashSets for O(1) lookup speed - much faster than List.any
    private val IGNORED_PATHS = hashSetOf("node_modules", ".git", "build", ".gradle", ".idea")
    private val IGNORED_FILES = hashSetOf("package-lock.json", "yarn.lock", ".DS_Store", "LICENSE")
    private val EXT_WHITE_LIST = hashSetOf("kt", "java", "xml", "json", "md", "txt", "gradle", "js", "py", "c", "cpp", "h")

    fun allFilesFlow(): Flow<List<PromptFileEntity>> = dao.observeAll()

    fun searchFiles(query: String): Flow<List<PromptFileEntity>> = dao.searchFiles(query)

    suspend fun readChunk(entity: PromptFileEntity, offset: Long, size: Int): Pair<String, Long> = withContext(Dispatchers.IO) {
        val file = File(entity.filePath)
        if (!file.exists()) return@withContext "" to -1L
        
        val buffer = ByteArray(size)
        runCatching {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(offset)
                val read = raf.read(buffer)
                if (read == -1) return@withContext "" to -1L
                
                val content = String(buffer, 0, read, Charsets.UTF_8)
                val nextOffset = if (offset + read >= raf.length()) -1L else offset + read
                content to nextOffset
            }
        }.getOrDefault("" to -1L)
    }

    suspend fun bundleContextForAI(
        entity: PromptFileEntity,
        includeTree: Boolean,
        includePreview: Boolean,
        includeSummary: Boolean,
        includeInstructions: Boolean
    ): String = withContext(Dispatchers.IO) {
        dao.updateLastAccessed(entity.id)
        
        val tree = if (includeTree) {
            entity.lastKnownTree ?: generateProjectTreeFromZip(entity, includePreview)
        } else ""

        StringBuilder(tree.length + 1024).apply {
            append("### DEV-AI CONTEXT: ").appendLine(entity.displayName)
            if (includeSummary) {
                append("#### SUMMARY\n").appendLine(entity.summary ?: "DevAI Project Context")
            }
            if (includeTree && tree.isNotBlank()) {
                append("#### DIRECTORY STRUCTURE\n```text\n").append(tree).appendLine("```")
            }
            if (includeInstructions) {
                append("\n#### TASK\nAnalyze the logic and provide implementation advice.")
            }
        }.toString()
    }

    suspend fun importUriAsFile(uri: Uri, displayName: String?): Long = withContext(Dispatchers.IO) {
        val nameHint = displayName ?: queryFileName(uri) ?: "file_${System.currentTimeMillis()}"
        val destFile = File(appContext.filesDir, nameHint)
        
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { out -> input.copyTo(out, 16384) }
        }

        val ext = nameHint.substringAfterLast('.', "").lowercase()
        val entity = PromptFileEntity(
            displayName = nameHint,
            filePath = destFile.absolutePath,
            extension = ext,
            language = if (ext == "zip") "zip" else "text",
            fileSizeBytes = destFile.length()
        )

        // FIX: Changed from .insert to .upsert to match the new DAO
        val id = dao.upsert(entity)
        
        val initialTree = generateProjectTreeFromZip(entity.copy(id = id), includeContent = true)
        dao.updateMetadataIndex(id, initialTree, "Initial project scan complete.")
        
        return@withContext id
    }

    suspend fun generateProjectTreeFromZip(
        entity: PromptFileEntity, 
        includeContent: Boolean
    ): String = withContext(Dispatchers.IO) {
        val zipFile = File(entity.filePath)
        if (!zipFile.exists()) return@withContext "File missing."

        val treeBuilder = StringBuilder(16384)
        val lineBuffer = ByteArray(2048)

        runCatching {
            ZipInputStream(BufferedInputStream(zipFile.inputStream(), IO_BUFFER_SIZE)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val path = entry.name
                    if (shouldIgnore(path)) {
                        entry = zip.nextEntry
                        continue
                    }

                    var depth = 0
                    for (char in path) if (char == '/') depth++
                    if (path.endsWith('/')) depth--

                    repeat(depth) { treeBuilder.append("  ") }
                    
                    val fileName = path.substringAfterLast('/', path).removeSuffix("/")
                    if (entry.isDirectory) {
                        treeBuilder.append("📁 ").appendLine(fileName)
                    } else {
                        treeBuilder.append("📄 ").appendLine(fileName)
                        
                        if (includeContent && looksLikeTextOrCode(fileName)) {
                            val indent = "  ".repeat(depth + 2)
                            val read = zip.read(lineBuffer)
                            if (read > 0) {
                                val chunk = String(lineBuffer, 0, read, Charsets.UTF_8)
                                chunk.lineSequence().take(5).forEach { line ->
                                    if (line.isNotBlank()) {
                                        treeBuilder.append(indent).appendLine(line.trim())
                                    }
                                }
                            }
                        }
                    }
                    entry = zip.nextEntry
                }
            }
            val result = treeBuilder.toString()
            dao.updateTree(entity.id, result)
            result
        }.getOrElse { "Scan failed: ${it.localizedMessage}" }
    }

    private fun shouldIgnore(path: String): Boolean {
        val segments = path.split('/')
        return segments.any { it in IGNORED_PATHS } || IGNORED_FILES.any { path.endsWith(it) }
    }

    private fun looksLikeTextOrCode(name: String): Boolean {
        return name.substringAfterLast('.', "").lowercase() in EXT_WHITE_LIST
    }

    suspend fun delete(entity: PromptFileEntity) = withContext(Dispatchers.IO) {
        dao.delete(entity)
        File(entity.filePath).delete()
    }

    private fun queryFileName(uri: Uri): String? {
        return runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()
    }
}

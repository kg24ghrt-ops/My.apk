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
import java.io.RandomAccessFile // CRITICAL: For readChunk
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class PromptRepository(private val context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    private val dao = db.promptFileDao()
    
    private val IO_BUFFER_SIZE = 16384 
    private val IGNORED_PATHS = listOf("/node_modules/", "/.git/", "/build/", "/.gradle/", "/.idea/")
    private val IGNORED_FILES = listOf("package-lock.json", "yarn.lock", ".DS_Store", "LICENSE")

    fun allFilesFlow(): Flow<List<PromptFileEntity>> = dao.observeAll()

    fun searchFiles(query: String): Flow<List<PromptFileEntity>> = dao.searchFiles(query)

    /**
     * NEW: Fixed the Unresolved Reference error.
     * Reads a specific chunk of a file without loading the whole thing into memory.
     */
    suspend fun readChunk(entity: PromptFileEntity, offset: Long, size: Int): Pair<String, Long> = withContext(Dispatchers.IO) {
        val file = File(entity.filePath)
        if (!file.exists()) return@withContext "" to -1L
        
        val buffer = ByteArray(size)
        try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(offset)
                val read = raf.read(buffer)
                if (read == -1) return@withContext "" to -1L
                
                val content = String(buffer, 0, read, Charsets.UTF_8)
                val nextOffset = if (offset + read >= raf.length()) -1L else offset + read
                content to nextOffset
            }
        } catch (e: Exception) {
            "" to -1L
        }
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
        } else null

        val summary = if (includeSummary) (entity.summary ?: "DevAI Project Context") else null

        StringBuilder().apply {
            append("### DEV-AI CONTEXT: ${entity.displayName}\n")
            if (includeSummary) append("#### SUMMARY\n$summary\n\n")
            
            if (includeTree && !tree.isNullOrBlank()) {
                append("#### DIRECTORY & SOURCE STRUCTURE\n")
                append("```text\n$tree\n```\n\n")
            }

            if (includeInstructions) {
                append("#### TASK & INSTRUCTIONS\n")
                append("Analyze the project logic and provide expert implementation advice.\n")
            }
        }.toString()
    }

    suspend fun importUriAsFile(uri: Uri, displayName: String?): Long = withContext(Dispatchers.IO) {
        val nameHint = displayName ?: queryFileName(uri) ?: "file_${System.currentTimeMillis()}"
        val originalFile = File(appContext.filesDir, nameHint)
        
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            originalFile.outputStream().use { out -> input.copyTo(out) }
        }

        val entity = PromptFileEntity(
            displayName = nameHint,
            filePath = originalFile.absolutePath,
            language = if (nameHint.endsWith(".zip")) "zip" else "text",
            fileSizeBytes = originalFile.length()
        )

        val id = dao.insert(entity)
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

        val treeBuilder = StringBuilder(8192)
        val buffer = ByteArray(IO_BUFFER_SIZE)

        try {
            ZipInputStream(BufferedInputStream(zipFile.inputStream(), IO_BUFFER_SIZE)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    if (shouldIgnore(entry.name)) {
                        zip.closeEntry()
                        entry = zip.nextEntry
                        continue
                    }

                    val depth = entry.name.count { it == '/' }
                    val indent = "  ".repeat(depth)
                    val fileName = entry.name.substringAfterLast('/')
                    
                    if (entry.isDirectory) {
                        treeBuilder.append("$indent📁 $fileName/\n")
                    } else {
                        treeBuilder.append("$indent📄 $fileName\n")
                        
                        if (includeContent && looksLikeTextOrCode(entry.name)) {
                            val contentIndent = "$indent    "
                            val bytesRead = zip.read(buffer)
                            if (bytesRead > 0) {
                                val chunk = String(buffer, 0, bytesRead, Charsets.UTF_8)
                                chunk.lineSequence().take(8).forEach { line ->
                                    if (line.isNotBlank()) treeBuilder.append("$contentIndent$line\n")
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            val result = treeBuilder.toString()
            dao.updateTree(entity.id, result)
            return@withContext result
        } catch (e: Exception) {
            return@withContext "Indexing error: ${e.localizedMessage}"
        }
    }

    private fun shouldIgnore(path: String) = 
        IGNORED_PATHS.any { path.contains(it) } || IGNORED_FILES.any { path.endsWith(it) }

    private fun looksLikeTextOrCode(name: String): Boolean {
        val ext = listOf(".kt", ".java", ".xml", ".json", ".md", ".txt", ".gradle", ".js", ".py")
        return ext.any { name.lowercase().endsWith(it) }
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
        } catch (e: Exception) { null }
    }
}

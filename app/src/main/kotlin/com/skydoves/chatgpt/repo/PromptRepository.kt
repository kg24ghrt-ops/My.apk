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

    private val IGNORED_PATHS = listOf("/node_modules/", "/.git/", "/build/", "/.gradle/", "/.idea/")
    private val IGNORED_FILES = listOf("package-lock.json", "yarn.lock", ".DS_Store", "LICENSE")

    fun allFilesFlow() = dao.observeAll()

    suspend fun bundleContextForAI(
        entity: PromptFileEntity,
        includeTree: Boolean,
        includePreview: Boolean,
        includeSummary: Boolean,
        includeInstructions: Boolean
    ): String = withContext(Dispatchers.IO) {
        dao.updateLastAccessed(entity.id)
        
        // FORCE REBUILD: We pass includePreview down to ensure the tree matches the toggle
        val tree = if (includeTree) generateProjectTreeFromZip(entity, includePreview) else null
        val summary = if (includeSummary) (entity.summary ?: "Standard Project Analysis") else null

        StringBuilder().apply {
            append("### DEV-AI CONTEXT: ${entity.displayName}\n")
            
            if (includeSummary) {
                append("#### SUMMARY\n$summary\n\n")
            }

            if (includeTree && !tree.isNullOrBlank()) {
                append("#### DIRECTORY & SOURCE STRUCTURE\n")
                append("```text\n$tree\n```\n\n")
            }

            if (includeInstructions) {
                append("#### TASK & INSTRUCTIONS\n")
                append("Analyze the provided project. Identify core logic and provide implementation advice.\n")
            }
        }.toString()
    }

    suspend fun generateProjectTreeFromZip(
        entity: PromptFileEntity, 
        includeContent: Boolean = false // NEW: Parameter to control content inclusion
    ): String = withContext(Dispatchers.IO) {
        val zipFile = File(entity.filePath)
        if (!zipFile.exists()) return@withContext "ZIP missing."

        val treeBuilder = StringBuilder()
        val MAX_FILE_CHARS = 2_000 

        try {
            ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zip ->
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
                        
                        // ONLY READ CONTENT IF PREVIEW TOGGLE IS ON
                        if (includeContent && looksLikeTextOrCode(entry.name)) {
                            val contentIndent = "$indent    "
                            val bytesRead = zip.read(sharedBuffer)
                            if (bytesRead > 0) {
                                val chunk = String(sharedBuffer, 0, bytesRead, Charsets.UTF_8)
                                chunk.lineSequence().take(10).forEach { line ->
                                    if (line.isNotBlank()) treeBuilder.append("$contentIndent$line\n")
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            return@withContext treeBuilder.toString()
        } catch (e: Exception) {
            return@withContext "Error: ${e.localizedMessage}"
        }
    }

    private fun shouldIgnore(path: String) = IGNORED_PATHS.any { path.contains(it) } || IGNORED_FILES.any { path.endsWith(it) }

    private fun looksLikeTextOrCode(name: String): Boolean {
        val ext = listOf(".kt", ".java", ".xml", ".json", ".md", ".txt", ".gradle", ".js", ".py")
        return ext.any { name.lowercase().endsWith(it) }
    }

    suspend fun importUriAsFile(uri: Uri, displayName: String?): Long = withContext(Dispatchers.IO) {
        val nameHint = displayName ?: queryFileName(uri) ?: "file_${System.currentTimeMillis()}"
        val originalFile = File(appContext.filesDir, nameHint)
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            originalFile.outputStream().use { out -> input.copyTo(out) }
        }
        val entity = PromptFileEntity(displayName = nameHint, filePath = originalFile.absolutePath, 
            language = if (nameHint.endsWith(".zip")) "zip" else "text", fileSizeBytes = originalFile.length())
        dao.insert(entity)
    }

    suspend fun readChunk(entity: PromptFileEntity, offset: Long, size: Int): Pair<String, Long> = withContext(Dispatchers.IO) {
        val file = File(entity.filePath)
        if (!file.exists()) return@withContext "" to -1L
        val buffer = ByteArray(size)
        java.io.RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            val read = raf.read(buffer)
            String(buffer, 0, read) to (if (offset + read >= raf.length()) -1L else offset + read)
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
        } catch (e: Exception) { null }
    }
}

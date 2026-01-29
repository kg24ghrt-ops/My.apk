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

    // ------------------- IMPORT -------------------
    suspend fun importUriAsFile(uri: Uri, displayName: String?): Long = withContext(Dispatchers.IO) {
        val nameHint = displayName ?: queryFileName(uri) ?: "file_${System.currentTimeMillis()}"
        val safeName = nameHint.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val originalFile = File(appContext.filesDir, "imported_$safeName")

        appContext.contentResolver.openInputStream(uri)?.use { input ->
            originalFile.outputStream().use { out -> input.copyTo(out) }
        } ?: throw IllegalArgumentException("Cannot open URI: $uri")

        if (safeName.endsWith(".zip", true) || safeName.endsWith(".jar", true)) {
            // If ZIP/JAR, keep original as entity
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

    // ------------------- DELETE -------------------
    suspend fun delete(entity: PromptFileEntity) = withContext(Dispatchers.IO) {
        dao.delete(entity)
        try { File(entity.filePath).deleteRecursively() } catch (_: Exception) {}
    }

    // ------------------- READ CHUNK -------------------
    suspend fun readChunk(entity: PromptFileEntity, offsetBytes: Long, chunkSizeBytes: Int): Pair<String, Long> =
        withContext(Dispatchers.IO) {
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
                val nextOffset = offsetBytes + actuallyRead
                Pair(text, if (nextOffset >= raf.length()) -1L else nextOffset)
            } finally {
                try { raf.close() } catch (_: Exception) {}
            }
        }

    // ------------------- PROJECT TREE GENERATOR -------------------
    /**
     * Generate a tree view of a ZIP file with text/code files.
     * Each file's content is included, indented by depth.
     */
    suspend fun generateProjectTreeFromZip(entity: PromptFileEntity): String = withContext(Dispatchers.IO) {
        val zipFile = File(entity.filePath)
        if (!zipFile.exists()) return@withContext "ZIP file does not exist."

        val treeBuilder = StringBuilder()
        openZipStream(zipFile).use { zip ->
            val buffer = ByteArray(8 * 1024)
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && looksLikeTextOrCode(entry.name)) {
                    val depth = entry.name.count { it == '/' }
                    treeBuilder.append("│   ".repeat(depth))
                    val fileName = entry.name.substringAfterLast('/')
                    treeBuilder.append("├─ $fileName\n")

                    val contentBuilder = StringBuilder()
                    var bytesRead = zip.read(buffer)
                    while (bytesRead > 0) {
                        contentBuilder.append(String(buffer, 0, bytesRead, Charsets.UTF_8))
                        bytesRead = zip.read(buffer)
                    }

                    contentBuilder.lines().forEach { line ->
                        treeBuilder.append("│   ".repeat(depth + 1))
                        treeBuilder.append(line).append("\n")
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        treeBuilder.toString()
    }

    // ---------- HELPERS ----------
    private fun openZipStream(zipFile: File) = ZipInputStream(BufferedInputStream(zipFile.inputStream()))

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        val resolver = appContext.contentResolver
        val cursor: Cursor? = try { resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null) } catch (_: Exception) { null }
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = it.getString(idx)
            }
        }
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
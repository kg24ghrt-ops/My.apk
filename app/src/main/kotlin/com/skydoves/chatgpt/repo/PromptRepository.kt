package com.skydoves.chatgpt.repo

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.skydoves.chatgpt.data.AppDatabase
import com.skydoves.chatgpt.data.entity.PromptFileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

// ------------------- ERROR MODEL -------------------
data class RepoError(
    val source: String,
    val message: String,
    val throwable: Throwable? = null
)

class PromptRepository(private val context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    private val dao = db.promptFileDao()

    // ------------------- ERROR REPORTING -------------------
    private val _errors = MutableSharedFlow<RepoError>(extraBufferCapacity = 8)
    val errors: SharedFlow<RepoError> = _errors

    fun allFilesFlow() = dao.observeAll()
    suspend fun getById(id: Long) = dao.getById(id)

    // ------------------- IMPORT -------------------
    suspend fun importUriAsFile(uri: Uri, displayName: String?): Long =
        withContext(Dispatchers.IO) {
            try {
                val nameHint = displayName ?: queryFileName(uri)
                    ?: "file_${System.currentTimeMillis()}"

                val safeName = nameHint.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val originalFile = File(appContext.filesDir, "imported_$safeName")

                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    originalFile.outputStream().use { out -> input.copyTo(out) }
                } ?: throw IllegalArgumentException("Cannot open URI: $uri")

                val entity = PromptFileEntity(
                    displayName = originalFile.name,
                    filePath = originalFile.absolutePath,
                    language = if (safeName.endsWith(".zip", true) || safeName.endsWith(".jar", true))
                        "zip" else guessLanguageFromName(safeName),
                    fileSizeBytes = originalFile.length()
                )

                dao.insert(entity)
            } catch (t: Throwable) {
                _errors.tryEmit(
                    RepoError(
                        source = "importUriAsFile",
                        message = "Failed to import file",
                        throwable = t
                    )
                )
                throw t
            }
        }

    // ------------------- DELETE -------------------
    suspend fun delete(entity: PromptFileEntity) =
        withContext(Dispatchers.IO) {
            try {
                dao.delete(entity)
                File(entity.filePath).deleteRecursively()
            } catch (t: Throwable) {
                _errors.tryEmit(
                    RepoError(
                        source = "delete",
                        message = "Failed to delete file",
                        throwable = t
                    )
                )
            }
        }

    // ------------------- READ CHUNK -------------------
    suspend fun readChunk(
        entity: PromptFileEntity,
        offsetBytes: Long,
        chunkSizeBytes: Int
    ): Pair<String, Long> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(entity.filePath)
                if (!file.exists() || file.length() == 0L) return@withContext "" to -1L

                val raf = java.io.RandomAccessFile(file, "r")
                raf.use {
                    if (offsetBytes >= it.length()) return@withContext "" to -1L

                    it.seek(offsetBytes)
                    val toRead = minOf(chunkSizeBytes, (it.length() - offsetBytes).toInt())
                    val buffer = ByteArray(toRead)

                    val read = it.read(buffer)
                    if (read <= 0) return@withContext "" to -1L

                    val text = String(buffer, 0, read, Charsets.UTF_8)
                    val next = offsetBytes + read
                    text to if (next >= it.length()) -1L else next
                }
            } catch (t: Throwable) {
                _errors.tryEmit(
                    RepoError(
                        source = "readChunk",
                        message = "Failed to read file chunk",
                        throwable = t
                    )
                )
                "" to -1L
            }
        }

    // ------------------- PROJECT TREE GENERATOR -------------------
    suspend fun generateProjectTreeFromZip(entity: PromptFileEntity): String =
        withContext(Dispatchers.IO) {
            val zipFile = File(entity.filePath)
            if (!zipFile.exists()) return@withContext "ZIP file does not exist."

            val tree = StringBuilder()

            try {
                openZipStream(zipFile).use { zip ->
                    val buffer = ByteArray(8 * 1024)
                    var entry: ZipEntry? = zip.nextEntry

                    while (entry != null) {
                        if (!entry.isDirectory && looksLikeTextOrCode(entry.name)) {
                            val depth = entry.name.count { it == '/' }
                            tree.append("│   ".repeat(depth))
                            tree.append("├─ ${entry.name.substringAfterLast('/')}\n")

                            val content = StringBuilder()
                            var read = zip.read(buffer)
                            while (read > 0) {
                                content.append(String(buffer, 0, read, Charsets.UTF_8))
                                read = zip.read(buffer)
                            }

                            content.lines().forEach { line ->
                                tree.append("│   ".repeat(depth + 1))
                                tree.append(line).append("\n")
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } catch (t: Throwable) {
                _errors.tryEmit(
                    RepoError(
                        source = "generateProjectTreeFromZip",
                        message = "Failed to read ZIP contents",
                        throwable = t
                    )
                )
                return@withContext "Failed to parse ZIP file."
            }

            tree.toString()
        }

    // ------------------- HELPERS -------------------
    private fun openZipStream(zipFile: File) =
        ZipInputStream(BufferedInputStream(zipFile.inputStream()))

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        val resolver = appContext.contentResolver

        val cursor: Cursor? = try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        } catch (_: Exception) {
            null
        }

        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = it.getString(idx)
            }
        }

        return name ?: uri.lastPathSegment
    }

    private fun looksLikeTextOrCode(name: String): Boolean {
        val lower = name.lowercase()
        return listOf(
            ".kt", ".java", ".xml", ".json", ".md", ".py", ".js", ".ts",
            ".c", ".cpp", ".h", ".txt", ".gradle", ".properties",
            ".yml", ".yaml", ".html", ".css", ".rb", ".go", ".rs",
            ".swift", ".php"
        ).any { lower.endsWith(it) }
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
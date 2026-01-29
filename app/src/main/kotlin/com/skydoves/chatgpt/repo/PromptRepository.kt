package com.skydoves.chatgpt.repo

import android.content.Context
import android.net.Uri
import com.skydoves.chatgpt.data.AppDatabase
import com.skydoves.chatgpt.data.entity.PromptFileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PromptRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val dao = db.promptFileDao()

    fun allFilesFlow() = dao.observeAll() // ✅ updated

    suspend fun getById(id: Long) = dao.getById(id)

    suspend fun importUriAsFile(uri: Uri, displayName: String?, descriptionPlaceholder: String? = null): Long {
        return withContext(Dispatchers.IO) {
            val inStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Cannot open URI")
            val safeName = (displayName ?: "file_${System.currentTimeMillis()}")
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val outFile = File(context.filesDir, "imported_$safeName")
            outFile.outputStream().use { out -> inStream.copyTo(out) }
            val size = outFile.length()
            val entity = PromptFileEntity(
                displayName = displayName ?: safeName,
                filePath = outFile.absolutePath,
                language = null,
                fileSizeBytes = size
            )
            dao.insert(entity)
        }
    }

    suspend fun delete(entity: PromptFileEntity) {
        withContext(Dispatchers.IO) {
            dao.delete(entity)
            try { File(entity.filePath).delete() } catch (_: Exception) {}
        }
    }

    suspend fun readChunk(entity: PromptFileEntity, offsetBytes: Long, chunkSizeBytes: Int): Pair<String, Long> {
        return withContext(Dispatchers.IO) {
            val file = File(entity.filePath)
            if (!file.exists()) return@withContext Pair("", -1L)
            val raf = java.io.RandomAccessFile(file, "r")
            try {
                if (offsetBytes >= raf.length()) return@withContext Pair("", -1L)
                raf.seek(offsetBytes)
                val toRead = minOf(chunkSizeBytes, (raf.length() - offsetBytes).toInt())
                val bytes = ByteArray(toRead)
                val actuallyRead = raf.read(bytes)
                val text = String(bytes, 0, maxOf(0, actuallyRead))
                val next = offsetBytes + actuallyRead
                val nextOffset = if (next >= raf.length()) -1L else next
                Pair(text, nextOffset)
            } finally {
                try { raf.close() } catch (_: Exception) {}
            }
        }
    }
}
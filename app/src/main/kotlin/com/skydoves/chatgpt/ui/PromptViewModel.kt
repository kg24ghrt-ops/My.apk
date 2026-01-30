package com.skydoves.chatgpt.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skydoves.chatgpt.data.entity.PromptFileEntity
import com.skydoves.chatgpt.repo.PromptRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter

class PromptViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PromptRepository(application.applicationContext)

    // ------------------- FILE LIST -------------------
    val filesFlow = repo.allFilesFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ------------------- ERROR REPORTING -------------------
    private val _errorFlow = MutableStateFlow<String?>(null)
    val errorFlow: StateFlow<String?> = _errorFlow

    private fun reportError(tag: String, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))

        _errorFlow.value =
            """
            ❌ ERROR: $tag
            
            Message:
            ${throwable.message ?: "No message"}
            
            Stack trace:
            $sw
            """.trimIndent()
    }

    fun clearError() {
        _errorFlow.value = null
    }

    // ------------------- IMPORT -------------------
    fun importUri(uri: Uri, displayName: String?) {
        viewModelScope.launch {
            try {
                repo.importUriAsFile(uri, displayName)
            } catch (e: Exception) {
                reportError("Import URI", e)
            }
        }
    }

    // ------------------- DELETE -------------------
    fun delete(entity: PromptFileEntity) {
        viewModelScope.launch {
            try {
                repo.delete(entity)
            } catch (e: Exception) {
                reportError("Delete file", e)
            }
        }
    }

    // ------------------- READ CHUNK -------------------
    suspend fun readChunk(
        entity: PromptFileEntity,
        offset: Long,
        chunkSize: Int
    ): Pair<String, Long> {
        return try {
            repo.readChunk(entity, offset, chunkSize)
        } catch (e: Exception) {
            reportError("Read chunk (${entity.displayName})", e)
            "" to -1L
        }
    }

    // ------------------- PROJECT TREE -------------------
    suspend fun generateProjectTree(entity: PromptFileEntity): String {
        return try {
            repo.generateProjectTreeFromZip(entity)
        } catch (e: Exception) {
            reportError("Generate project tree (${entity.displayName})", e)
            "❌ Failed to generate project tree.\nSee error report above."
        }
    }
}
package com.skydoves.chatgpt.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skydoves.chatgpt.data.entity.PromptFileEntity
import com.skydoves.chatgpt.repo.PromptRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter

class PromptViewModel(application: Application) : AndroidViewModel(application) {

  private val repo = PromptRepository(application.applicationContext)

  // ------------------- OBSERVABLE STATES -------------------

  // List of imported files from Room DB
  val filesFlow = repo.allFilesFlow()
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  // Currently displayed file content (Chunked Reading)
  private val _selectedFileContent = MutableStateFlow<String?>(null)
  val selectedFileContent: StateFlow<String?> = _selectedFileContent.asStateFlow()

  // Currently generated project hierarchy (Project Tree)
  private val _activeProjectTree = MutableStateFlow<String?>(null)
  val activeProjectTree: StateFlow<String?> = _activeProjectTree.asStateFlow()

  // Error reporting for UI
  private val _errorFlow = MutableStateFlow<String?>(null)
  val errorFlow: StateFlow<String?> = _errorFlow.asStateFlow()

  // ------------------- PRIVATE HELPERS -------------------

  private fun reportError(tag: String, throwable: Throwable) {
    val sw = StringWriter()
    throwable.printStackTrace(PrintWriter(sw))
    val message = buildString {
      append("❌ $tag\n")
      append(throwable.message ?: "No message")
      append("\n\nStack (truncated):\n")
      append(sw.toString().take(1200))
    }
    _errorFlow.value = message
  }

  fun clearError() { _errorFlow.value = null }

  // ------------------- ACTIONS (DEV-AI FEATURES) -------------------

  fun importUri(uri: Uri, displayName: String?) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        repo.importUriAsFile(uri, displayName)
      } catch (e: Exception) {
        reportError("Import URI", e)
      }
    }
  }

  /**
   * Loads the first 64KB of a file into the preview flow.
   * Implements: File Preview & Chunked Reading.
   */
  fun loadFilePreview(entity: PromptFileEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        // Offset 0, Chunk size 64KB for safety
        val (content, _) = repo.readChunk(entity, 0L, 64 * 1024)
        _selectedFileContent.value = content
      } catch (e: Exception) {
        reportError("Read chunk (${entity.displayName})", e)
      }
    }
  }

  /**
   * Generates a text-based tree for the UI.
   * Implements: Project Tree Generation.
   */
  fun requestProjectTree(entity: PromptFileEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      _activeProjectTree.value = "⏳ Generating tree..."
      try {
        val tree = repo.generateProjectTreeFromZip(entity)
        _activeProjectTree.value = tree
      } catch (e: Exception) {
        reportError("Generate project tree (${entity.displayName})", e)
        _activeProjectTree.value = "❌ Generation failed."
      }
    }
  }

  fun closePreview() {
    _selectedFileContent.value = null
    _activeProjectTree.value = null
  }

  fun delete(entity: PromptFileEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        repo.delete(entity)
      } catch (e: Exception) {
        reportError("Delete file", e)
      }
    }
  }

  // Exposed as a suspend function if specific manual chunking is needed
  suspend fun readChunk(entity: PromptFileEntity, offset: Long, chunkSize: Int) =
    try {
      repo.readChunk(entity, offset, chunkSize)
    } catch (e: Exception) {
      reportError("Read chunk (${entity.displayName})", e)
      "" to -1L
    }

  suspend fun generateProjectTree(entity: PromptFileEntity): String =
    try {
      repo.generateProjectTreeFromZip(entity)
    } catch (e: Exception) {
      reportError("Generate project tree (${entity.displayName})", e)
      "❌ Failed to generate project tree."
    }
}

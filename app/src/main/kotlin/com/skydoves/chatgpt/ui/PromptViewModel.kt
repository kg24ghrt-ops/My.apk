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

  val filesFlow = repo.allFilesFlow()
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  private val _selectedFileContent = MutableStateFlow<String?>(null)
  val selectedFileContent: StateFlow<String?> = _selectedFileContent.asStateFlow()

  private val _activeProjectTree = MutableStateFlow<String?>(null)
  val activeProjectTree: StateFlow<String?> = _activeProjectTree.asStateFlow()

  // New State for the AI Context Wrapper output
  private val _aiContextBundle = MutableStateFlow<String?>(null)
  val aiContextBundle: StateFlow<String?> = _aiContextBundle.asStateFlow()

  private val _isProcessing = MutableStateFlow(false)
  val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

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
        reportError("Import URI", it)
      }
    }
  }

  /**
   * The "AI Context Wrapper" trigger.
   * Bundles tree, content, and metadata into one string for the user to copy.
   */
  fun prepareAIContext(entity: PromptFileEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      _isProcessing.value = true
      try {
        val bundle = repo.bundleContextForAI(entity)
        _aiContextBundle.value = bundle
      } catch (e: Exception) {
        reportError("AI Bundle", e)
      } finally {
        _isProcessing.value = false
      }
    }
  }

  fun loadFilePreview(entity: PromptFileEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val (content, _) = repo.readChunk(entity, 0L, 64 * 1024)
        _selectedFileContent.value = content
      } catch (e: Exception) {
        reportError("Read chunk (${entity.displayName})", e)
      }
    }
  }

  fun requestProjectTree(entity: PromptFileEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      _activeProjectTree.value = "⏳ Processing tree..."
      try {
        // If the repository already has the tree cached in DB, this is now instant
        val tree = repo.generateProjectTreeFromZip(entity)
        _activeProjectTree.value = tree
      } catch (e: Exception) {
        reportError("Tree Gen", e)
        _activeProjectTree.value = "❌ Generation failed."
      }
    }
  }

  fun closePreview() {
    _selectedFileContent.value = null
    _activeProjectTree.value = null
    _aiContextBundle.value = null
  }

  fun delete(entity: PromptFileEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        repo.delete(entity)
      } catch (e: Exception) {
        reportError("Delete", e)
      }
    }
  }

  // Support for legacy chunking if needed
  suspend fun readChunk(entity: PromptFileEntity, offset: Long, chunkSize: Int) =
    repo.readChunk(entity, offset, chunkSize)
}

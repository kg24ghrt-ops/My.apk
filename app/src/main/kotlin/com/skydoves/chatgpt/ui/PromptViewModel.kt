package com.skydoves.chatgpt.ui

import android.app.Application
import android.net.Uri
import android.util.LruCache
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

  // OPTIMIZATION: Memory Cache for Bundles (Key: FileId, Value: Formatted String)
  // Keeps the last 10 bundles in high-speed RAM
  private val bundleCache = LruCache<Long, String>(10)

  // ------------------- OBSERVABLE STATES -------------------

  val filesFlow = repo.allFilesFlow()
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  private val _selectedFileContent = MutableStateFlow<String?>(null)
  val selectedFileContent: StateFlow<String?> = _selectedFileContent.asStateFlow()

  private val _activeProjectTree = MutableStateFlow<String?>(null)
  val activeProjectTree: StateFlow<String?> = _activeProjectTree.asStateFlow()

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
      append("❌ $tag [${System.currentTimeMillis()}]\n")
      append(throwable.message ?: "Unknown Error")
      append("\n\nStack Trace:\n")
      append(sw.toString().take(800)) // Truncate to keep UI responsive
    }
    _errorFlow.value = message
  }

  fun clearError() { _errorFlow.value = null }

  // ------------------- ACTIONS (DEV-AI OPTIMIZED) -------------------

  fun importUri(uri: Uri, displayName: String?) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        repo.importUriAsFile(uri, displayName)
      } catch (e: Exception) {
        reportError("Import", e)
      }
    }
  }

  /**
   * High-Speed Context Preparation.
   * Uses LruCache to deliver instant results for previously processed files.
   */
  fun prepareAIContext(entity: PromptFileEntity) {
    // Return early if already processing to save CPU
    if (_isProcessing.value) return 

    // Check Cache first
    val cached = bundleCache.get(entity.id)
    if (cached != null) {
      _aiContextBundle.value = cached
      return
    }

    viewModelScope.launch(Dispatchers.IO) {
      _isProcessing.value = true
      try {
        val bundle = repo.bundleContextForAI(entity)
        bundleCache.put(entity.id, bundle) // Save to RAM
        _aiContextBundle.value = bundle
      } catch (e: Exception) {
        reportError("Context Creation", e)
      } finally {
        _isProcessing.value = false
      }
    }
  }

  fun loadFilePreview(entity: PromptFileEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        // Limit preview to 32KB for massive performance gain in Compose rendering
        val (content, _) = repo.readChunk(entity, 0L, 32 * 1024)
        _selectedFileContent.value = content
      } catch (e: Exception) {
        reportError("Preview", e)
      }
    }
  }

  fun requestProjectTree(entity: PromptFileEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      _activeProjectTree.value = "⏳ Loading cached tree..."
      try {
        val tree = repo.generateProjectTreeFromZip(entity)
        _activeProjectTree.value = tree
      } catch (e: Exception) {
        reportError("Tree Generator", e)
        _activeProjectTree.value = "❌ Build Failed"
      }
    }
  }

  fun closePreview() {
    _selectedFileContent.value = null
    _activeProjectTree.value = null
    _aiContextBundle.value = null
    // We don't clear the bundleCache here so it stays hot for the next click
  }

  fun delete(entity: PromptFileEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        bundleCache.remove(entity.id) // Clean RAM
        repo.delete(entity)
      } catch (e: Exception) {
        reportError("Delete", e)
      }
    }
  }
}

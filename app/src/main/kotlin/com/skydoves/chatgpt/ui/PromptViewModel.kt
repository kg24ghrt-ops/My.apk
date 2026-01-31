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
    private val bundleCache = LruCache<Long, String>(10)

    private val _includeTree = MutableStateFlow(true)
    val includeTree = _includeTree.asStateFlow()

    private val _includePreview = MutableStateFlow(true)
    val includePreview = _includePreview.asStateFlow()

    private val _includeSummary = MutableStateFlow(true)
    val includeSummary = _includeSummary.asStateFlow()

    private val _includeInstructions = MutableStateFlow(true)
    val includeInstructions = _includeInstructions.asStateFlow()

    fun toggleTree(value: Boolean) { _includeTree.value = value }
    fun togglePreview(value: Boolean) { _includePreview.value = value }
    fun toggleSummary(value: Boolean) { _includeSummary.value = value }
    fun toggleInstructions(value: Boolean) { _includeInstructions.value = value }

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

    private fun reportError(tag: String, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        _errorFlow.value = "❌ $tag: ${throwable.message}"
    }

    fun clearError() { _errorFlow.value = null }

    fun importUri(uri: Uri, displayName: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try { repo.importUriAsFile(uri, displayName) } 
            catch (e: Exception) { reportError("Import", e) }
        }
    }

    fun prepareAIContext(entity: PromptFileEntity) {
        if (_isProcessing.value) return 
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            try {
                val bundle = repo.bundleContextForAI(
                    entity = entity,
                    includeTree = _includeTree.value,
                    includePreview = _includePreview.value,
                    includeSummary = _includeSummary.value,
                    includeInstructions = _includeInstructions.value
                )
                _aiContextBundle.value = bundle
            } catch (e: Exception) {
                reportError("Context", e)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun loadFilePreview(entity: PromptFileEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (content, _) = repo.readChunk(entity, 0L, 32 * 1024)
                _selectedFileContent.value = if (isBinaryContent(content)) {
                    "[Binary Content - Preview Disabled]"
                } else content
            } catch (e: Exception) { reportError("Preview", e) }
        }
    }

    private fun isBinaryContent(text: String): Boolean {
        if (text.isEmpty()) return false
        return text.count { it == '\u0000' } > 0 || text.take(100).any { it.code < 32 && it != '\n' && it != '\r' && it != '\t' }
    }

    fun requestProjectTree(entity: PromptFileEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            _activeProjectTree.value = "⏳ Loading..."
            try {
                _activeProjectTree.value = repo.generateProjectTreeFromZip(entity)
            } catch (e: Exception) { reportError("Tree", e) }
        }
    }

    fun closePreview() {
        _selectedFileContent.value = null
        _activeProjectTree.value = null
        _aiContextBundle.value = null
    }

    fun delete(entity: PromptFileEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try { repo.delete(entity) } 
            catch (e: Exception) { reportError("Delete", e) }
        }
    }
}

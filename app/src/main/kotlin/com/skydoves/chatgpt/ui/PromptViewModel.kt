package com.skydoves.chatgpt.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skydoves.chatgpt.data.entity.PromptFileEntity
import com.skydoves.chatgpt.repo.PromptRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter

enum class BundlePreset {
    CODE_REVIEW, ARCH_ONLY, BUG_FIX, QUICK_TASK
}

class PromptViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PromptRepository(application.applicationContext)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _includeTree = MutableStateFlow(true)
    val includeTree = _includeTree.asStateFlow()

    private val _includePreview = MutableStateFlow(true)
    val includePreview = _includePreview.asStateFlow()

    private val _includeSummary = MutableStateFlow(true)
    val includeSummary = _includeSummary.asStateFlow()

    private val _includeInstructions = MutableStateFlow(true)
    val includeInstructions = _includeInstructions.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val filesFlow = _searchQuery
        .debounce(300L) 
        .distinctUntilChanged() 
        .flatMapLatest { query ->
            if (query.isBlank()) repo.allFilesFlow()
            else repo.searchFiles(query) 
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope, 
            started = SharingStarted.WhileSubscribed(5000), 
            initialValue = emptyList()
        )

    private val _selectedFileContent = MutableStateFlow<String?>(null)
    val selectedFileContent = _selectedFileContent.asStateFlow()

    private val _activeProjectTree = MutableStateFlow<String?>(null)
    val activeProjectTree = _activeProjectTree.asStateFlow()

    private val _aiContextBundle = MutableStateFlow<String?>(null)
    val aiContextBundle = _aiContextBundle.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _errorFlow = MutableStateFlow<String?>(null)
    val errorFlow = _errorFlow.asStateFlow()

    fun updateSearchQuery(query: String) { _searchQuery.value = query }

    fun toggleTree(value: Boolean) { _includeTree.value = value }
    fun togglePreview(value: Boolean) { _includePreview.value = value }
    fun toggleSummary(value: Boolean) { _includeSummary.value = value }
    fun toggleInstructions(value: Boolean) { _includeInstructions.value = value }

    fun applyPreset(preset: BundlePreset) {
        when (preset) {
            BundlePreset.CODE_REVIEW -> { toggleTree(true); togglePreview(true); toggleSummary(true); toggleInstructions(true) }
            BundlePreset.ARCH_ONLY -> { toggleTree(true); togglePreview(false); toggleSummary(true); toggleInstructions(false) }
            BundlePreset.BUG_FIX -> { toggleTree(true); togglePreview(true); toggleSummary(false); toggleInstructions(true) }
            BundlePreset.QUICK_TASK -> { toggleTree(false); togglePreview(false); toggleSummary(true); toggleInstructions(true) }
        }
    }

    fun importUri(uri: Uri, displayName: String?) {
        viewModelScope.launch {
            _isProcessing.value = true
            try { repo.importUriAsFile(uri, displayName) } 
            catch (e: Exception) { reportError("Import", e) }
            finally { _isProcessing.value = false }
        }
    }

    fun prepareAIContext(entity: PromptFileEntity) {
        if (_isProcessing.value) return 
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                _aiContextBundle.value = repo.bundleContextForAI(
                    entity = entity,
                    includeTree = _includeTree.value,
                    includePreview = _includePreview.value,
                    includeSummary = _includeSummary.value,
                    includeInstructions = _includeInstructions.value
                )
            } catch (e: Exception) { reportError("Bundling", e) }
            finally { _isProcessing.value = false }
        }
    }

    fun loadFilePreview(entity: PromptFileEntity) {
        viewModelScope.launch {
            try {
                // This call requires the readChunk function to exist in PromptRepository
                val (content, _) = repo.readChunk(entity, 0L, 32768)
                _selectedFileContent.value = if (isBinaryContent(content)) {
                    "📂 [Binary/Large File] Preview hidden for stability."
                } else content
            } catch (e: Exception) { reportError("Preview", e) }
        }
    }

    fun requestProjectTree(entity: PromptFileEntity) {
        viewModelScope.launch {
            _activeProjectTree.value = "⏳ Scanning structure..."
            try {
                _activeProjectTree.value = repo.generateProjectTreeFromZip(entity, false)
            } catch (e: Exception) { reportError("Tree", e) }
        }
    }

    private fun isBinaryContent(text: String): Boolean {
        if (text.isEmpty()) return false
        return text.take(200).any { it.code < 32 && it != '\n' && it != '\r' && it != '\t' }
    }

    fun closePreview() {
        _selectedFileContent.value = null
        _activeProjectTree.value = null
        _aiContextBundle.value = null
    }

    fun delete(entity: PromptFileEntity) {
        viewModelScope.launch {
            try { repo.delete(entity) } 
            catch (e: Exception) { reportError("Delete", e) }
        }
    }

    private fun reportError(tag: String, t: Throwable) {
        _errorFlow.value = "❌ $tag: ${t.localizedMessage ?: "Unknown Error"}"
    }

    fun clearError() { _errorFlow.value = null }
}

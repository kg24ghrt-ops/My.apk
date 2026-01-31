package com.skydoves.chatgpt.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skydoves.chatgpt.data.entity.PromptFileEntity
import com.skydoves.chatgpt.repo.PromptRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Optimization: Presets for rapid configuration
 */
enum class BundlePreset {
    CODE_REVIEW, ARCH_ONLY, BUG_FIX, QUICK_TASK
}

class PromptViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PromptRepository(application.applicationContext)

    // --- SEARCH OPTIMIZATION ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // --- PACKAGING STRATEGY STATES ---
    private val _includeTree = MutableStateFlow(true)
    val includeTree = _includeTree.asStateFlow()

    private val _includePreview = MutableStateFlow(true)
    val includePreview = _includePreview.asStateFlow()

    private val _includeSummary = MutableStateFlow(true)
    val includeSummary = _includeSummary.asStateFlow()

    private val _includeInstructions = MutableStateFlow(true)
    val includeInstructions = _includeInstructions.asStateFlow()

    // --- TOGGLE & PRESET ACTIONS ---
    fun toggleTree(value: Boolean) { _includeTree.value = value }
    fun togglePreview(value: Boolean) { _includePreview.value = value }
    fun toggleSummary(value: Boolean) { _includeSummary.value = value }
    fun toggleInstructions(value: Boolean) { _includeInstructions.value = value }

    fun applyPreset(preset: BundlePreset) {
        when (preset) {
            BundlePreset.CODE_REVIEW -> {
                toggleTree(true); togglePreview(true); toggleSummary(true); toggleInstructions(true)
            }
            BundlePreset.ARCH_ONLY -> {
                toggleTree(true); togglePreview(false); toggleSummary(true); toggleInstructions(false)
            }
            BundlePreset.BUG_FIX -> {
                toggleTree(true); togglePreview(true); toggleSummary(false); toggleInstructions(true)
            }
            BundlePreset.QUICK_TASK -> {
                toggleTree(false); togglePreview(false); toggleSummary(true); toggleInstructions(true)
            }
        }
    }

    // --- WORKSPACE STATES (Optimized with Search) ---
    @OptIn(ExperimentalCoroutinesApi::class)
    val filesFlow = _searchQuery
        .flatMapLatest { query ->
            if (query.isEmpty()) repo.allFilesFlow()
            else repo.searchFiles(query) 
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun updateSearchQuery(query: String) { _searchQuery.value = query }

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
        _errorFlow.value = "❌ $tag Error: ${throwable.localizedMessage ?: "Unknown failure"}"
    }

    fun clearError() { _errorFlow.value = null }

    // --- ACTIONS ---
    fun importUri(uri: Uri, displayName: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            try { repo.importUriAsFile(uri, displayName) } 
            catch (e: Exception) { reportError("Import", e) }
            finally { _isProcessing.value = false }
        }
    }

    fun prepareAIContext(entity: PromptFileEntity) {
        if (_isProcessing.value) return 
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            try {
                // Uses optimized cached results in Repository for 50ms bundling
                val bundle = repo.bundleContextForAI(
                    entity = entity,
                    includeTree = _includeTree.value,
                    includePreview = _includePreview.value,
                    includeSummary = _includeSummary.value,
                    includeInstructions = _includeInstructions.value
                )
                _aiContextBundle.value = bundle
            } catch (e: Exception) {
                reportError("Context Bundling", e)
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
                    "[Binary File Detected - Preview Disabled for Safety]"
                } else content
            } catch (e: Exception) { reportError("Preview", e) }
        }
    }

    private fun isBinaryContent(text: String): Boolean {
        if (text.isEmpty()) return false
        return text.count { it == '\u0000' } > 0 || 
               text.take(100).any { it.code < 32 && it != '\n' && it != '\r' && it != '\t' }
    }

    fun requestProjectTree(entity: PromptFileEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            _activeProjectTree.value = "⏳ Generating File Tree..."
            try {
                _activeProjectTree.value = repo.generateProjectTreeFromZip(entity, includeContent = false)
            } catch (e: Exception) { reportError("Tree Generation", e) }
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

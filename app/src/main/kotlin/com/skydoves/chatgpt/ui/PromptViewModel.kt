package com.skydoves.chatgpt.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skydoves.chatgpt.data.entity.PromptFileEntity
import com.skydoves.chatgpt.repo.PromptRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PromptViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = PromptRepository(application.applicationContext)

    val filesFlow = repo.allFilesFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun importUri(uri: Uri, displayName: String?) {
        viewModelScope.launch {
            try { repo.importUriAsFile(uri, displayName) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun delete(entity: PromptFileEntity) {
        viewModelScope.launch { repo.delete(entity) }
    }

    suspend fun readChunk(entity: PromptFileEntity, offset: Long, chunkSize: Int) =
        repo.readChunk(entity, offset, chunkSize)

    // ------------------- NEW: Project tree from ZIP -------------------
    suspend fun generateProjectTree(entity: PromptFileEntity): String =
        repo.generateProjectTreeFromZip(entity)
}
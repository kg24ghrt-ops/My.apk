package com.skydoves.chatgpt.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prompt_files")
data class PromptFileEntity(
    @PrimaryKey(autoGenerate = true) 
    val id: Long = 0,
    
    // --- Core Metadata ---
    val displayName: String,
    val filePath: String,
    val language: String? = null,
    val fileSizeBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    
    // --- AI Context Wrapper Fields ---
    
    /**
     * Stores the last successfully generated project tree.
     * This allows the "Context Wrapper" to bundle the tree instantly.
     */
    val lastKnownTree: String? = null,

    /**
     * AI-generated or user-defined summary of the file's purpose.
     */
    val summary: String? = null,

    /**
     * User-defined patterns to exclude from tree generation (e.g., "node_modules, .git").
     */
    val customIgnorePatterns: String? = null,

    /**
     * Categorization tags (e.g., "Feature", "Bugfix", "Legacy").
     */
    val tags: String? = null,

    // --- UI State & Workflow Management ---
    
    val isFavorite: Boolean = false,
    
    val isArchived: Boolean = false,

    /**
     * Updated every time this file is bundled for an AI prompt.
     */
    val lastAccessedAt: Long = System.currentTimeMillis(),

    /**
     * Quick-access extension for language-specific formatting in prompts.
     */
    val extension: String? = displayName.substringAfterLast('.', "")
)

package com.skydoves.chatgpt.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prompt_files")
data class PromptFileEntity(
    @PrimaryKey(autoGenerate = true) 
    val id: Long = 0,
    
    // Original fields
    val displayName: String,
    val filePath: String,
    val language: String? = null,
    val fileSizeBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    
    // --- New DevAI Infrastructure ---
    
    /**
     * Stores a short summary of the file or project content.
     * Used for the "Context Management" feature.
     */
    val summary: String? = null,

    /**
     * Comma-separated list of tags for filtering.
     * Example: "Experimental, Kotlin, Core"
     */
    val tags: String? = null,

    /**
     * Project-specific ignore patterns for Tree Generation.
     * Example: "temp/, logs/, *.tmp"
     */
    val customIgnorePatterns: String? = null,

    /**
     * Allows soft-deletion/archiving to keep the workspace clean.
     */
    val isArchived: Boolean = false,

    /**
     * Last time this file was used in a "Copy for AI" workflow.
     */
    val lastAccessedAt: Long = System.currentTimeMillis()
)

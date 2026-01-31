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
    
    // --- AI Context Wrapper Caching ---
    /**
     * Cache for the directory structure. 
     * Optimization: We read this instead of re-scanning the ZIP.
     */
    val lastKnownTree: String? = null,

    /**
     * Cache for the project summary.
     */
    val summary: String? = null,

    /**
     * Patterns like "node_modules, .git" to keep the AI focus sharp.
     */
    val customIgnorePatterns: String? = "node_modules, .git, build, .gradle",

    val tags: String? = null,

    // --- State Management ---
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val lastAccessedAt: Long = System.currentTimeMillis(),

    /**
     * Computed at creation for high-speed UI filtering.
     */
    val extension: String? = displayName.substringAfterLast('.', "")
)

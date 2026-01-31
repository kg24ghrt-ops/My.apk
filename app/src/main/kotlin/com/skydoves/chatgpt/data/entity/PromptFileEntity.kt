package com.skydoves.chatgpt.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "prompt_files",
    indices = [
        Index(value = ["displayName"]), 
        Index(value = ["extension"]),   
        Index(value = ["lastAccessedAt"]), 
        Index(value = ["filePath"], unique = true) 
    ]
)
data class PromptFileEntity(
    @PrimaryKey(autoGenerate = true) 
    val id: Long = 0,
    
    @ColumnInfo(name = "displayName")
    val displayName: String,
    
    @ColumnInfo(name = "filePath")
    val filePath: String,
    
    val language: String? = null,
    val fileSizeBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "lastKnownTree")
    val lastKnownTree: String? = null,

    val summary: String? = null,

    val customIgnorePatterns: String? = "node_modules, .git, build, .gradle",

    val tags: String? = null,

    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    
    @ColumnInfo(name = "lastAccessedAt")
    val lastAccessedAt: Long = System.currentTimeMillis(),

    // REMOVED LOGIC: This is now a pure data field. 
    // Optimization: Calculate this in the Repository during import, not in the UI.
    @ColumnInfo(name = "extension")
    val extension: String? = null 
)

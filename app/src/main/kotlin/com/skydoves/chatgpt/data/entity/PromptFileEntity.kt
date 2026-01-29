package com.skydoves.chatgpt.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prompt_files")
data class PromptFileEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val displayName: String,
  val filePath: String, // internal path where file content is stored
  val language: String? = null,
  val fileSizeBytes: Long = 0,
  val createdAt: Long = System.currentTimeMillis()
)
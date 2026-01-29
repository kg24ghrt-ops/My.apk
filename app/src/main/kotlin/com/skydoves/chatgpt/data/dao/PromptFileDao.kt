package com.skydoves.chatgpt.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow
import com.skydoves.chatgpt.data.entity.PromptFileEntity

@Dao
interface PromptFileDao {
  @Query("SELECT * FROM prompt_files ORDER BY createdAt DESC")
  fun getAllFlow(): Flow<List<PromptFileEntity>>

  @Query("SELECT * FROM prompt_files WHERE id = :id")
  suspend fun getById(id: Long): PromptFileEntity?

  @Insert
  suspend fun insert(entity: PromptFileEntity): Long

  @Delete
  suspend fun delete(entity: PromptFileEntity)
}
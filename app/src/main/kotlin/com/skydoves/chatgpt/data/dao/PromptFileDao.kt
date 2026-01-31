package com.skydoves.chatgpt.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.skydoves.chatgpt.data.entity.PromptFileEntity

@Dao
interface PromptFileDao {

  @Query("SELECT * FROM prompt_files WHERE isArchived = 0 ORDER BY isFavorite DESC, createdAt DESC")
  fun observeAll(): Flow<List<PromptFileEntity>>

  @Query("SELECT * FROM prompt_files WHERE id = :id LIMIT 1")
  suspend fun getById(id: Long): PromptFileEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entity: PromptFileEntity): Long

  /**
   * Updates existing file metadata (Summary, Tree, Tags).
   * Essential for the AI Context Wrapper workflow.
   */
  @Update
  suspend fun update(entity: PromptFileEntity)

  @Query("UPDATE prompt_files SET lastKnownTree = :tree WHERE id = :id")
  suspend fun updateTree(id: Long, tree: String)

  @Query("UPDATE prompt_files SET lastAccessedAt = :timestamp WHERE id = :id")
  suspend fun updateLastAccessed(id: Long, timestamp: Long = System.currentTimeMillis())

  @Delete
  suspend fun delete(entity: PromptFileEntity)

  @Query("DELETE FROM prompt_files WHERE id = :id")
  suspend fun deleteById(id: Long)
}

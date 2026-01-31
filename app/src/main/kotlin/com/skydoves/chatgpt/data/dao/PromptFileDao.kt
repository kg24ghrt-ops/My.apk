package com.skydoves.chatgpt.data.dao

import androidx.room.*
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

  // MISSING FUNCTION FIX: Added for line 141 in Repository
  @Query("UPDATE prompt_files SET lastKnownTree = :tree WHERE id = :id")
  suspend fun updateTree(id: Long, tree: String)

  @Query("UPDATE prompt_files SET lastKnownTree = :tree, summary = :metadataIndex WHERE id = :id")
  suspend fun updateMetadataIndex(id: Long, tree: String, metadataIndex: String)

  @Query("UPDATE prompt_files SET lastAccessedAt = :timestamp WHERE id = :id")
  suspend fun updateLastAccessed(id: Long, timestamp: Long = System.currentTimeMillis())

  // SEARCH FIX: Updated to ensure it handles the search query correctly
  @Query("SELECT * FROM prompt_files WHERE (displayName LIKE '%' || :query || '%' OR language LIKE '%' || :query || '%') AND isArchived = 0")
  fun searchFiles(query: String): Flow<List<PromptFileEntity>>

  @Delete
  suspend fun delete(entity: PromptFileEntity)

  @Query("DELETE FROM prompt_files WHERE id = :id")
  suspend fun deleteById(id: Long)
}

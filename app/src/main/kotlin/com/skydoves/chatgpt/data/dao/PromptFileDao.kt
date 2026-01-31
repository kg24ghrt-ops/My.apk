package com.skydoves.chatgpt.data.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.skydoves.chatgpt.data.entity.PromptFileEntity

@Dao
interface PromptFileDao {

    // Optimized sorting: Uses lastAccessedAt so your latest work is always on top
    @Query("SELECT * FROM prompt_files WHERE isArchived = 0 ORDER BY isFavorite DESC, lastAccessedAt DESC")
    fun observeAll(): Flow<List<PromptFileEntity>>

    @Query("SELECT * FROM prompt_files WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PromptFileEntity?

    // 🚀 WAY BETTER: Upsert preserves your ID and existing metadata instead of deleting/recreating
    @Upsert
    suspend fun upsert(entity: PromptFileEntity): Long

    @Query("UPDATE prompt_files SET lastKnownTree = :tree WHERE id = :id")
    suspend fun updateTree(id: Long, tree: String)

    @Query("UPDATE prompt_files SET lastKnownTree = :tree, summary = :metadataIndex WHERE id = :id")
    suspend fun updateMetadataIndex(id: Long, tree: String, metadataIndex: String)

    @Query("UPDATE prompt_files SET lastAccessedAt = :timestamp WHERE id = :id")
    suspend fun updateLastAccessed(id: Long, timestamp: Long = System.currentTimeMillis())

    // 🔍 SEARCH OPTIMIZATION: Removed leading '%' where possible to allow Index usage.
    // Also added search by extension for "pro" filtering.
    @Query("""
        SELECT * FROM prompt_files 
        WHERE isArchived = 0 AND (
            displayName LIKE :query || '%' 
            OR displayName LIKE '% ' || :query || '%'
            OR extension = :query
        )
        ORDER BY isFavorite DESC, lastAccessedAt DESC
    """)
    fun searchFiles(query: String): Flow<List<PromptFileEntity>>

    @Delete
    suspend fun delete(entity: PromptFileEntity)

    @Query("DELETE FROM prompt_files WHERE id = :id")
    suspend fun deleteById(id: Long)
}

package com.skydoves.chatgpt.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.skydoves.chatgpt.data.entity.PromptFileEntity

@Dao
interface PromptFileDao {

    @Query("SELECT * FROM prompt_files ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PromptFileEntity>>

    @Query("SELECT * FROM prompt_files WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PromptFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PromptFileEntity): Long

    @Delete
    suspend fun delete(entity: PromptFileEntity)

    @Query("DELETE FROM prompt_files WHERE id = :id")
    suspend fun deleteById(id: Long)
}
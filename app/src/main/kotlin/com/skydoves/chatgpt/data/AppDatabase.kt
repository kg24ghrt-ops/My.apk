package com.skydoves.chatgpt.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.skydoves.chatgpt.data.dao.PromptFileDao
import com.skydoves.chatgpt.data.entity.PromptFileEntity

@Database(
  entities = [PromptFileEntity::class],
  version = 2, // Incremented to 2 to support new DevAI entity columns
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

  abstract fun promptFileDao(): PromptFileDao

  companion object {
    @Volatile private var INSTANCE: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase =
      INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "prompt_app.db"
        )
          // Automatically wipes and recreates the DB when the schema changes.
          // Ideal for rapid development of the DevAI Assistant features.
          .fallbackToDestructiveMigration() 
          .build().also { INSTANCE = it }
      }
  }
}

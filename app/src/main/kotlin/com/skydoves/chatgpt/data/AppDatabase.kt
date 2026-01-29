package com.skydoves.chatgpt.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.skydoves.chatgpt.data.dao.PromptFileDao
import com.skydoves.chatgpt.data.entity.PromptFileEntity

@Database(
    entities = [PromptFileEntity::class],
    version = 1,
    exportSchema = false // prevents Room schema export warnings
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun promptFileDao(): PromptFileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "prompt_app.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
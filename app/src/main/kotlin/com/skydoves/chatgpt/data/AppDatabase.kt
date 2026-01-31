package com.skydoves.chatgpt.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.skydoves.chatgpt.data.dao.PromptFileDao
import com.skydoves.chatgpt.data.entity.PromptFileEntity

@Database(
    entities = [PromptFileEntity::class],
    version = 3, // Supporting new Indices and Pre-computed Extensions
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun promptFileDao(): PromptFileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "prompt_app.db"
            )
            // 🛠️ Development Safety: Wipes DB if schema changes to avoid crashes
            .fallbackToDestructiveMigration()
            
            // 🚀 PERFORMANCE: Concurrent Read/Write (No more UI locking during imports)
            .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
            
            .addCallback(object : Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    // 🧠 RAM OPTIMIZATION: Move temporary tables and sorting to memory
                    db.execSQL("PRAGMA cache_size = -4000;") // ~4MB Cache
                    db.execSQL("PRAGMA temp_store = MEMORY;") 
                    db.execSQL("PRAGMA synchronous = NORMAL;") // Balances speed/safety in WAL mode
                }
            })
            .build()
        }
    }
}

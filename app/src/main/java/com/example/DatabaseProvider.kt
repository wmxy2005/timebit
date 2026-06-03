package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.TimeTrackDatabase

object DatabaseProvider {
    @Volatile
    private var database: TimeTrackDatabase? = null

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE time_logs ADD COLUMN isAutoTimer INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE time_logs ADD COLUMN timerType INTEGER NOT NULL DEFAULT 0")
        }
    }

    fun getDatabase(context: Context): TimeTrackDatabase {
        return database ?: synchronized(this) {
            database ?: Room.databaseBuilder(
                context.applicationContext,
                TimeTrackDatabase::class.java,
                "time_track_database"
            )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()
            .also { database = it }
        }
    }
}

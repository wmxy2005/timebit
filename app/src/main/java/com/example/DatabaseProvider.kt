package com.example

import android.content.Context
import androidx.room.Room
import com.example.data.TimeTrackDatabase

object DatabaseProvider {
    @Volatile
    private var database: TimeTrackDatabase? = null

    fun getDatabase(context: Context): TimeTrackDatabase {
        return database ?: synchronized(this) {
            database ?: Room.databaseBuilder(
                context.applicationContext,
                TimeTrackDatabase::class.java,
                "time_track_database"
            )
            .fallbackToDestructiveMigration()
            .build()
            .also { database = it }
        }
    }
}

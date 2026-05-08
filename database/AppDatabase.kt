package com.example.cognishield.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
@Database(
    entities = [SuppressedNotification::class],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun notificationDao(): NotificationDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cogni_db"
                ).build()

                INSTANCE = instance

                instance
            }
        }
    }
}
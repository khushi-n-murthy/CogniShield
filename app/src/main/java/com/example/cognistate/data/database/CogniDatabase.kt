package com.example.cognistate.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.cognistate.data.dao.CogniEventDao
import com.example.cognistate.data.entities.CogniEvent
import com.example.cognistate.data.entities.SuppressedNotif

@Database(
    entities = [
        CogniEvent::class,
        SuppressedNotif::class
    ],
    version = 1
)
abstract class CogniDatabase : RoomDatabase() {

    abstract fun cogniEventDao(): CogniEventDao

    companion object {

        @Volatile
        private var INSTANCE: CogniDatabase? = null

        fun getDatabase(
            context: Context
        ): CogniDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance =
                    Room.databaseBuilder(
                        context.applicationContext,
                        CogniDatabase::class.java,
                        "cogni_database"
                    ).build()

                INSTANCE = instance

                instance
            }
        }
    }
}
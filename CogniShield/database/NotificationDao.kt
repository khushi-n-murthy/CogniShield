package com.example.cognishield.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface NotificationDao {
    @Insert
    suspend fun insert(notification: SuppressedNotification)

    @Query("SELECT * FROM suppressed_notifs")
    suspend fun getAll(): List<SuppressedNotification>
}
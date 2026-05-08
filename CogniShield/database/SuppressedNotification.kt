package com.example.cognishield.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "suppressed_notifs")
data class SuppressedNotification(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val text: String
)
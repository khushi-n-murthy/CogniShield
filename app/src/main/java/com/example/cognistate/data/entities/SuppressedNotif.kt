package com.example.cognistate.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "suppressed_notifs")
data class SuppressedNotif(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val packageName: String,

    val title: String,

    val text: String,

    val timestamp: Long
)
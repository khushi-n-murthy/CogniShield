package com.example.cognistate.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cogni_events")
data class CogniEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val timestamp: Long,

    val stressScore: Float,

    val stateLabel: String,

    val edaRaw: Float,

    val hrvRaw: Float,

    val gazeScore: Float
)
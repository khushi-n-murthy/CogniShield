package com.example.cognistate.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cognistate.data.entities.SuppressedNotif
import kotlinx.coroutines.flow.Flow

@Dao
interface SuppressedNotifDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notif: SuppressedNotif)

    @Query("""
        SELECT * FROM suppressed_notifs
        WHERE timestamp BETWEEN :startTime AND :endTime
        ORDER BY timestamp DESC
    """)
    fun queryByDay(
        startTime: Long,
        endTime: Long
    ): Flow<List<SuppressedNotif>>
}

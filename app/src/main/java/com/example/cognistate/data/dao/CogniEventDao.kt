package com.example.cognistate.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cognistate.data.entities.CogniEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface CogniEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: CogniEvent)

    @Query(
        """
        SELECT * FROM cogni_events
        WHERE timestamp BETWEEN :startOfDay AND :endOfDay
        ORDER BY timestamp ASC
        """
    )
    fun queryByDay(
        startOfDay: Long,
        endOfDay: Long
    ): Flow<List<CogniEvent>>
}
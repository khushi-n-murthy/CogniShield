package com.example.cognistate.data.repository

import com.example.cognistate.data.dao.CogniEventDao
import com.example.cognistate.data.entities.CogniEvent
import kotlinx.coroutines.flow.Flow

class SessionRepository(
    private val cogniEventDao: CogniEventDao
) {

    suspend fun insertEvent(
        event: CogniEvent
    ) {

        cogniEventDao.insert(event)
    }

    fun getEventsByDay(
        startOfDay: Long,
        endOfDay: Long
    ): Flow<List<CogniEvent>> {

        return cogniEventDao.queryByDay(
            startOfDay,
            endOfDay
        )
    }
}
package com.example.facemesh.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.random.Random

data class SessionDataPoint(
    val timestamp: Long,
    val stressScore: Float,
    val isPeakRedline: Boolean
)

interface SessionRepository {
    fun getWeeklyHistory(): Flow<List<SessionDataPoint>>
}

class MockSessionRepository : SessionRepository {
    override fun getWeeklyHistory(): Flow<List<SessionDataPoint>> {
        val dataPoints = mutableListOf<SessionDataPoint>()
        
        // Start 7 days ago
        val now = LocalDateTime.now()
        val startDateTime = now.minusDays(7)
        
        // Randomly pick 5 to 10 indices to be "peak redline"
        val totalHours = 168 // 7 days * 24 hours
        val peakCount = Random.nextInt(5, 11)
        val peakIndices = mutableSetOf<Int>()
        while (peakIndices.size < peakCount) {
            peakIndices.add(Random.nextInt(0, totalHours))
        }

        for (i in 0 until totalHours) {
            val currentHour = startDateTime.plusHours(i.toLong())
            val timestamp = currentHour.toEpochSecond(ZoneOffset.UTC) * 1000 // In milliseconds
            val isPeak = peakIndices.contains(i)
            
            // Peak redlines should logically have high stress scores, let's say between 0.8 and 1.0.
            // Normal values between 0.0 and 0.79.
            val stressScore = if (isPeak) {
                Random.nextFloat() * 0.2f + 0.8f
            } else {
                Random.nextFloat() * 0.8f
            }

            dataPoints.add(SessionDataPoint(timestamp, stressScore, isPeak))
        }

        return flowOf(dataPoints)
    }
}

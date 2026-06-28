package com.lockin.app.core.domain.usecase

import com.lockin.app.core.domain.model.Session
import com.lockin.app.core.domain.model.SessionStatus
import com.lockin.app.core.domain.repository.SessionRepository
import com.lockin.app.core.security.EncryptedPrefsManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/**
 * Use case to calculate the user's current daily focus streak dynamically from session history.
 * The streak is defined as the number of consecutive days with at least one COMPLETED session.
 * If there is no completed session today or yesterday, the current streak is 0.
 */
class GetStreakUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val encryptedPrefsManager: EncryptedPrefsManager
) {

    /**
     * Streams the current focus streak count.
     *
     * @return A Flow emitting the current consecutive day streak count as an Int.
     */
    operator fun invoke(): Flow<Int> {
        return sessionRepository.getAllSessionsFlow().map { sessions ->
            val userId = encryptedPrefsManager.getUserId()
            val filteredSessions = if (userId != null) {
                sessions.filter { it.userId == userId }
            } else {
                sessions
            }
            calculateCurrentStreak(filteredSessions)
        }
    }

    /**
     * Calculates the current streak of consecutive days with successful sessions.
     */
    private fun calculateCurrentStreak(sessions: List<Session>): Int {
        val completedSessions = sessions.filter { it.status == SessionStatus.COMPLETED }
        if (completedSessions.isEmpty()) return 0

        // Format dates in the local time zone to identify unique successful days
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }

        val completedDates = completedSessions.map { session ->
            val timestamp = session.actualEndTime ?: session.targetEndTime
            dateFormat.format(Date(timestamp))
        }.toSet()

        val calendar = Calendar.getInstance()
        val todayStr = dateFormat.format(calendar.time)

        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = dateFormat.format(calendar.time)

        // Streak continues if there is a successful session either today or yesterday
        val startStr = when {
            completedDates.contains(todayStr) -> todayStr
            completedDates.contains(yesterdayStr) -> yesterdayStr
            else -> return 0
        }

        // Walk backwards day-by-day starting from the startStr
        var streak = 0
        val cursor = Calendar.getInstance()
        
        if (startStr == yesterdayStr) {
            cursor.add(Calendar.DAY_OF_YEAR, -1) // start cursor at yesterday
        }

        while (true) {
            val cursorStr = dateFormat.format(cursor.time)
            if (completedDates.contains(cursorStr)) {
                streak++
                cursor.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }

        return streak
    }
}

/*
 * File: app/src/main/java/com/lockin/app/feature/history/HistoryViewModel.kt
 * Purpose: ViewModel for the Session History feature of LockIn.
 * Aggregates focus session history logs, calculates statistics (streaks,
 * completion rates, total focus durations), and retrieves session event timelines.
 */

package com.lockin.app.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lockin.app.core.domain.model.Session
import com.lockin.app.core.domain.model.SessionEvent
import com.lockin.app.core.domain.model.SessionStatus
import com.lockin.app.core.domain.repository.SessionRepository
import com.lockin.app.core.domain.usecase.GetSessionHistoryUseCase
import com.lockin.app.core.domain.usecase.GetStreakUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/**
 * UI State data payload containing session logs and aggregated statistics.
 */
data class HistoryUiStateData(
    val sessions: List<Session> = emptyList(),
    val totalSessionsCount: Int = 0,
    val completionRatePercentage: Float = 0f,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalTimeLockedInMs: Long = 0L
)

/**
 * Sealed UI State definition for the History screen.
 */
sealed interface HistoryUiState {
    object Loading : HistoryUiState
    data class Success(val data: HistoryUiStateData) : HistoryUiState
    data class Error(val message: String) : HistoryUiState
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getSessionHistoryUseCase: GetSessionHistoryUseCase,
    private val getStreakUseCase: GetStreakUseCase,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _selectedSessionEvents = MutableStateFlow<List<SessionEvent>>(emptyList())
    val selectedSessionEvents: StateFlow<List<SessionEvent>> = _selectedSessionEvents.asStateFlow()

    // Combine history list and current streak counts reactively
    val uiState: StateFlow<HistoryUiState> = combine(
        getSessionHistoryUseCase(),
        getStreakUseCase()
    ) { sessions, currentStreak ->
        try {
            val total = sessions.size
            val completedCount = sessions.count { it.status == SessionStatus.COMPLETED }
            val completionRate = if (total > 0) (completedCount.toFloat() / total) * 100f else 0f
            
            // Sum of milliseconds for all completed sessions
            val totalFocusTimeMs = sessions.filter { it.status == SessionStatus.COMPLETED }
                .sumOf { (it.actualEndTime ?: it.targetEndTime) - it.startTime }

            val longest = calculateLongestStreak(sessions)

            val data = HistoryUiStateData(
                sessions = sessions,
                totalSessionsCount = total,
                completionRatePercentage = completionRate,
                currentStreak = currentStreak,
                longestStreak = longest,
                totalTimeLockedInMs = totalFocusTimeMs
            )
            HistoryUiState.Success(data)
        } catch (e: Exception) {
            Timber.e(e, "Error composing History UI state flows")
            HistoryUiState.Error(e.message ?: "Failed to load session logs")
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState.Loading
    )

    /**
     * Loads the granular events/heartbeats trail for a particular session to show in the detail pane.
     *
     * @param sessionId The targeted session ID.
     */
    fun loadEventsForSession(sessionId: String) {
        viewModelScope.launch {
            try {
                val events = sessionRepository.getEventsForSession(sessionId)
                _selectedSessionEvents.value = events.sortedBy { it.timestamp }
                Timber.d("Loaded %d events for session: %s", events.size, sessionId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load events for session %s", sessionId)
            }
        }
    }

    /**
     * Clears the active selected session's events list to close the detail panel.
     */
    fun clearSelectedSessionEvents() {
        _selectedSessionEvents.value = emptyList()
    }

    /**
     * Calculates the longest consecutive daily completed session streak.
     *
     * @param sessions List of all sessions.
     * @return The longest streak as number of days.
     */
    private fun calculateLongestStreak(sessions: List<Session>): Int {
        val completedSessions = sessions.filter { it.status == SessionStatus.COMPLETED }
        if (completedSessions.isEmpty()) return 0

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }

        // Extract unique, sorted dates representing successful focus days
        val completedDates = completedSessions.map { session ->
            val timestamp = session.actualEndTime ?: session.targetEndTime
            dateFormat.format(Date(timestamp))
        }.distinct().sorted()

        if (completedDates.isEmpty()) return 0

        var longest = 1
        var current = 1
        val calendar = Calendar.getInstance()

        for (i in 0 until completedDates.size - 1) {
            val date1 = dateFormat.parse(completedDates[i])
            val date2 = dateFormat.parse(completedDates[i + 1])
            
            if (date1 != null && date2 != null) {
                calendar.time = date1
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                val nextDayStr = dateFormat.format(calendar.time)
                
                if (nextDayStr == completedDates[i + 1]) {
                    current++
                } else {
                    longest = maxOf(longest, current)
                    current = 1
                }
            }
        }
        longest = maxOf(longest, current)
        return longest
    }
}

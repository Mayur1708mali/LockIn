/*
 * File: com/lockin/app/feature/session/SessionViewModel.kt
 * Purpose: ViewModel for the Active Session screen.
 * Tracks remaining focus session seconds, updates the UI reactively down to the second,
 * and calls the CompleteSessionUseCase when the focus timer runs down to 0.
 */

package com.lockin.app.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lockin.app.core.domain.model.Session
import com.lockin.app.core.domain.model.SessionStatus
import com.lockin.app.core.domain.repository.SessionRepository
import com.lockin.app.core.domain.usecase.CompleteSessionUseCase
import com.lockin.app.core.domain.usecase.GetActiveSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * UI State definition for the Active Session countdown screen.
 */
sealed interface SessionUiState {
    /**
     * Waiting to resolve session status from database on initial screen launch.
     */
    object Loading : SessionUiState

    /**
     * Active session successfully resolved.
     *
     * @param sessionId Unique ID of the focus session.
     * @param remainingSeconds Time left in seconds.
     * @param penaltyAmountPaise Stakes amount held in paise.
     * @param totalDurationSeconds Total duration of the detox session.
     */
    data class Success(
        val sessionId: String,
        val remainingSeconds: Long,
        val penaltyAmountPaise: Int,
        val totalDurationSeconds: Long
    ) : SessionUiState

    /**
     * No active session could be found in the database.
     */
    object Error : SessionUiState
}

/**
 * ViewModel processing inputs and timer countdowns for the active focus session.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val getActiveSessionUseCase: GetActiveSessionUseCase,
    private val completeSessionUseCase: CompleteSessionUseCase,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SessionUiState>(SessionUiState.Loading)
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private val _finishedSession = MutableStateFlow<Session?>(null)
    /**
     * StateFlow emitting the completed/broken session details once it finishes.
     */
    val finishedSession: StateFlow<Session?> = _finishedSession.asStateFlow()

    private var countdownJob: Job? = null

    init {
        loadActiveSession()
        observeActiveSessionLifecycle()
    }

    /**
     * Observes the active session in database. When the active session is cleared (completed/broken),
     * queries the final session state to trigger UI completion routing.
     */
    private fun observeActiveSessionLifecycle() {
        viewModelScope.launch {
            sessionRepository.getActiveSessionFlow().collectLatest { activeSession ->
                if (activeSession == null) {
                    val currentSuccess = _uiState.value as? SessionUiState.Success
                    if (currentSuccess != null) {
                        val finalSession = sessionRepository.getSessionById(currentSuccess.sessionId)
                        if (finalSession != null && finalSession.status != SessionStatus.ACTIVE) {
                            _finishedSession.value = finalSession
                        }
                    }
                }
            }
        }
    }

    /**
     * Loads the active session from database to configure timer stakes and schedules the countdown.
     */
    fun loadActiveSession() {
        viewModelScope.launch {
            try {
                val session = getActiveSessionUseCase()
                if (session != null) {
                    val totalDuration = (session.targetEndTime - session.startTime) / 1000
                    startCountdown(session, totalDuration)
                } else {
                    _uiState.value = SessionUiState.Error
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading active session.")
                _uiState.value = SessionUiState.Error
            }
        }
    }

    /**
     * Ticks a 1-second interval coroutine, updating remainingSeconds reactively.
     * Invokes database session completion on expiration.
     */
    private fun startCountdown(session: Session, totalDuration: Long) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val remainingMs = session.targetEndTime - now
                val remainingSeconds = (remainingMs + 999) / 1000 // ceiling division to seconds

                if (remainingSeconds <= 0L) {
                    _uiState.value = SessionUiState.Success(
                        sessionId = session.sessionId,
                        remainingSeconds = 0L,
                        penaltyAmountPaise = session.penaltyAmount,
                        totalDurationSeconds = totalDuration
                    )
                    completeSession()
                    break
                } else {
                    _uiState.value = SessionUiState.Success(
                        sessionId = session.sessionId,
                        remainingSeconds = remainingSeconds,
                        penaltyAmountPaise = session.penaltyAmount,
                        totalDurationSeconds = totalDuration
                    )
                }
                delay(1000)
            }
        }
    }

    /**
     * Atomically releases locked funds and updates session state to COMPLETED.
     */
    internal fun completeSession() {
        viewModelScope.launch {
            try {
                val result = completeSessionUseCase()
                if (result.isSuccess) {
                    Timber.d("ActiveSession completed successfully.")
                } else {
                    Timber.e("Failed to complete session: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error running CompleteSessionUseCase.")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}

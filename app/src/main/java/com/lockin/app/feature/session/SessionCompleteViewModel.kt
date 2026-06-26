/*
 * File: com/lockin/app/feature/session/SessionCompleteViewModel.kt
 * Purpose: ViewModel for the Session Completion/End Screen.
 * Fetches updated wallet balances, current focus streaks, and original session stakes to render outcomes.
 */

package com.lockin.app.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lockin.app.core.domain.model.SessionStatus
import com.lockin.app.core.domain.repository.SessionRepository
import com.lockin.app.core.domain.usecase.GetStreakUseCase
import com.lockin.app.core.domain.usecase.GetWalletUseCase
import com.lockin.app.core.security.EncryptedPrefsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * UI State definition for the Session Complete Screen.
 */
data class SessionCompleteUiState(
    val status: SessionStatus = SessionStatus.COMPLETED,
    val penaltyAmountPaise: Int = 0,
    val availableBalancePaise: Int = 0,
    val streakCount: Int = 0,
    val originalDurationSeconds: Long = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * ViewModel processing metrics for the final detox session outcome page.
 */
@HiltViewModel
class SessionCompleteViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val getWalletUseCase: GetWalletUseCase,
    private val getStreakUseCase: GetStreakUseCase,
    private val encryptedPrefsManager: EncryptedPrefsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionCompleteUiState())
    val uiState: StateFlow<SessionCompleteUiState> = _uiState.asStateFlow()

    /**
     * Initializes state bindings, fetching the session outcome and loading wallet stats.
     *
     * @param sessionId Session unique identifier.
     * @param statusString The outcome status string ("COMPLETED" or "BROKEN").
     */
    fun initSessionResult(sessionId: String, statusString: String) {
        val parsedStatus = try {
            SessionStatus.valueOf(statusString.uppercase())
        } catch (e: Exception) {
            SessionStatus.COMPLETED
        }

        val userId = encryptedPrefsManager.getUserId() ?: "default_user"

        _uiState.update {
            it.copy(
                status = parsedStatus,
                isLoading = true,
                error = null
            )
        }

        // Fetch session stakes details and observe streaks
        viewModelScope.launch {
            try {
                val session = sessionRepository.getSessionById(sessionId)
                if (session != null) {
                    val duration = (session.targetEndTime - session.startTime) / 1000
                    _uiState.update {
                        it.copy(
                            penaltyAmountPaise = session.penaltyAmount,
                            originalDurationSeconds = duration
                        )
                    }
                }

                getStreakUseCase().collectLatest { streak ->
                    _uiState.update { it.copy(streakCount = streak) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading session details or streak in SessionCompleteViewModel")
            }
        }

        // Stream wallet available balance updates
        viewModelScope.launch {
            try {
                getWalletUseCase(userId).collectLatest { wallet ->
                    _uiState.update {
                        it.copy(
                            availableBalancePaise = wallet.availableBalance,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error streaming wallet balance updates in SessionCompleteViewModel")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}

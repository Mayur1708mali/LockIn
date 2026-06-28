/*
 * File: app/src/main/java/com/lockin/app/feature/account/AccountViewModel.kt
 * Purpose: ViewModel for the Account feature of LockIn.
 * Exposes profile info, session history metrics, streaks, and handles user sign-out.
 */

package com.lockin.app.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lockin.app.core.domain.model.SessionStatus
import com.lockin.app.core.domain.usecase.GetSessionHistoryUseCase
import com.lockin.app.core.domain.usecase.GetStreakUseCase
import com.lockin.app.core.domain.usecase.SignOutUseCase
import com.lockin.app.core.security.EncryptedPrefsManager
import com.lockin.app.core.util.AuthEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Sealed interface representing UI States for the Sign-Out execution flow.
 */
sealed interface UiState {
    object Idle : UiState
    object Loading : UiState
    object Success : UiState
    data class Error(val message: String) : UiState
}

/**
 * UI State for the Account screen.
 */
data class AccountUiState(
    val displayName: String = "",
    val email: String = "",
    val memberSince: String = "",
    val totalSessions: Int = 0,
    val totalTimeLocked: String = "0h 0m",
    val currentStreak: Int = 0
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val encryptedPrefsManager: EncryptedPrefsManager,
    private val getSessionHistoryUseCase: GetSessionHistoryUseCase,
    private val getStreakUseCase: GetStreakUseCase,
    private val signOutUseCase: SignOutUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    private val _signOutState = MutableStateFlow<UiState>(UiState.Idle)
    val signOutState: StateFlow<UiState> = _signOutState.asStateFlow()

    private var currentLoadedUserId: String? = null
    private var statsJob: kotlinx.coroutines.Job? = null

    init {
        refreshData()
    }

    /**
     * Refreshes user details and sets up/resets stats stream when user context changes.
     * Why: Correctly updates display details and aggregates metrics on a per-user basis.
     */
    fun refreshData() {
        val userId = encryptedPrefsManager.getUserId() ?: "default_user"
        val name = encryptedPrefsManager.getGoogleDisplayName() ?: "User"
        val userEmail = encryptedPrefsManager.getGoogleEmail() ?: "user@example.com"
        val since = encryptedPrefsManager.getMemberSince() ?: run {
            val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            val formatted = dateFormat.format(Date())
            encryptedPrefsManager.saveMemberSince(formatted)
            formatted
        }

        _uiState.update {
            it.copy(
                displayName = name,
                email = userEmail,
                memberSince = since
            )
        }

        if (userId != currentLoadedUserId) {
            statsJob?.cancel()
            currentLoadedUserId = userId

            statsJob = viewModelScope.launch {
                combine(
                    getSessionHistoryUseCase(),
                    getStreakUseCase()
                ) { sessions, streak ->
                    val userSessions = sessions.filter { it.userId == userId }
                    val completedCount = userSessions.count { it.status == SessionStatus.COMPLETED }
                    val totalMs = userSessions.filter { it.status == SessionStatus.COMPLETED }
                        .sumOf { (it.actualEndTime ?: it.targetEndTime) - it.startTime }

                    AccountUiState(
                        displayName = name,
                        email = userEmail,
                        memberSince = since,
                        totalSessions = completedCount,
                        totalTimeLocked = formatDuration(totalMs),
                        currentStreak = streak
                    )
                }.collectLatest { updatedState ->
                    _uiState.value = updatedState
                }
            }
        }
    }

    /**
     * Signs out the user, clearing JWT, display details, auto-topup config and signals navigation flow.
     * Why: Restores the application to onboarding state securely.
     *
     * @param onComplete Callback executed once sign out processes successfully.
     */
    fun signOut(onComplete: () -> Unit = {}) {
        _signOutState.value = UiState.Loading
        viewModelScope.launch {
            try {
                signOutUseCase()
                _signOutState.value = UiState.Success
                AuthEventBus.postUnauthorized() // Trigger global navigation rewrite
                onComplete()
                Timber.i("AccountViewModel: User signed out successfully.")
            } catch (e: Exception) {
                Timber.e(e, "AccountViewModel: Sign-out failed.")
                _signOutState.value = UiState.Error(e.localizedMessage ?: "Sign-out failed")
            }
        }
    }

    /**
     * Helper utility to convert a millisecond duration into an hours and minutes text string.
     * Why: Formats raw longs to "14h 20m" style.
     */
    private fun formatDuration(totalTimeMs: Long): String {
        val totalMinutes = totalTimeMs / (1000 * 60)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return "${hours}h ${minutes}m"
    }
}

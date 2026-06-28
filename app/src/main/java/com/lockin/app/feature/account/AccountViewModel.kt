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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val encryptedPrefsManager: EncryptedPrefsManager,
    private val getSessionHistoryUseCase: GetSessionHistoryUseCase,
    private val getStreakUseCase: GetStreakUseCase,
    private val signOutUseCase: SignOutUseCase
) : ViewModel() {

    // Cache static fields locally upon launch
    val displayName: String = encryptedPrefsManager.getGoogleDisplayName() ?: "User"
    val email: String = encryptedPrefsManager.getGoogleEmail() ?: "user@example.com"
    
    // Store or read the signup month and year on first account display
    val memberSince: String = encryptedPrefsManager.getMemberSince() ?: run {
        val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val formatted = dateFormat.format(Date())
        encryptedPrefsManager.saveMemberSince(formatted)
        formatted
    }

    private val _signOutState = MutableStateFlow<UiState>(UiState.Idle)
    val signOutState: StateFlow<UiState> = _signOutState.asStateFlow()

    /**
     * Exposes total completed sessions count computed reactively from history logs.
     * Why: Separates concerns by projecting database domain logs into UI-specific stats.
     */
    val totalSessions: StateFlow<Int> = getSessionHistoryUseCase()
        .map { sessions ->
            sessions.count { it.status == SessionStatus.COMPLETED }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    /**
     * Exposes formatted focus hours and minutes computed from completed focus sessions.
     * Why: Aggregates millisecond durations and maps to "14h 20m" layout representation.
     */
    val totalTimeLocked: StateFlow<String> = getSessionHistoryUseCase()
        .map { sessions ->
            val totalMs = sessions.filter { it.status == SessionStatus.COMPLETED }
                .sumOf { (it.actualEndTime ?: it.targetEndTime) - it.startTime }
            formatDuration(totalMs)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "0h 0m"
        )

    /**
     * Exposes the current consecutive daily focus streak count.
     * Why: Connects to streak calculation logic to display inside metrics cards.
     */
    val currentStreak: StateFlow<Int> = getStreakUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

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

/*
 * File: app/src/main/java/com/lockin/app/navigation/LaunchViewModel.kt
 * Purpose: ViewModel for app startup launch routing.
 * Evaluates the startup destination based on active sessions and wallet balance.
 */

package com.lockin.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lockin.app.core.domain.usecase.GetActiveSessionUseCase
import com.lockin.app.core.domain.usecase.GetWalletUseCase
import com.lockin.app.core.security.EncryptedPrefsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Sealed interface representing the state of the app launch destination selection.
 */
sealed interface LaunchState {
    /**
     * Currently evaluating which screen to launch.
     */
    object Loading : LaunchState

    /**
     * Launch destination determined.
     * @param route The computed initial navigation route.
     */
    data class Destination(val route: LockInRoute) : LaunchState
}

/**
 * ViewModel that coordinates the entry logic of the LockIn application.
 */
@HiltViewModel
class LaunchViewModel @Inject constructor(
    private val getActiveSessionUseCase: GetActiveSessionUseCase,
    private val getWalletUseCase: GetWalletUseCase,
    private val encryptedPrefsManager: EncryptedPrefsManager
) : ViewModel() {

    private val _launchState = MutableStateFlow<LaunchState>(LaunchState.Loading)
    val launchState: StateFlow<LaunchState> = _launchState.asStateFlow()

    init {
        determineStartDestination()
    }

    /**
     * Determines whether the user should be routed to Onboarding, Home, or directly to an ActiveSession.
     * High priority is given to any running session to prevent users from bypassing focus mode.
     */
    private fun determineStartDestination() {
        viewModelScope.launch {
            try {
                // 1. If an active session exists, immediately route to the ActiveSession screen.
                val activeSession = getActiveSessionUseCase()
                if (activeSession != null) {
                    val remainingTimeSeconds = (activeSession.targetEndTime - System.currentTimeMillis()) / 1000
                    Timber.d("Launch logic: Active session found. Directing to ActiveSession with ${remainingTimeSeconds}s remaining.")
                    _launchState.value = LaunchState.Destination(
                        LockInRoute.ActiveSession(
                            sessionId = activeSession.sessionId,
                            durationSeconds = maxOf(0L, remainingTimeSeconds),
                            penaltyAmountPaise = activeSession.penaltyAmount
                        )
                    )
                    return@launch
                }

                // 2. If the user is already signed in (JWT exists and is valid), go straight to Home.
                val jwt = encryptedPrefsManager.getAuthJwt()
                if (!jwt.isNullOrEmpty()) {
                    Timber.d("Launch logic: User signed in with valid JWT. Directing to Home.")
                    _launchState.value = LaunchState.Destination(LockInRoute.Home)
                    return@launch
                }

                // 3. Otherwise, route the user to the Onboarding screen.
                Timber.d("Launch logic: No active session or valid JWT. Directing to Onboarding.")
                _launchState.value = LaunchState.Destination(LockInRoute.Onboarding)

            } catch (e: Exception) {
                Timber.e(e, "Error determining launch destination. Defaulting to Onboarding.")
                _launchState.value = LaunchState.Destination(LockInRoute.Onboarding)
            }
        }
    }
}

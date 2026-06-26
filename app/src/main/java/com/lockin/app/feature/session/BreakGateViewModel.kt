/*
 * File: com/lockin/app/feature/session/BreakGateViewModel.kt
 * Purpose: ViewModel orchestrating the three-step digital detox break-early gate.
 * Controls warning step timers, biometric results integration, and final typed text matches.
 */

package com.lockin.app.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.fragment.app.FragmentActivity
import com.lockin.app.core.domain.usecase.BreakSessionUseCase
import com.lockin.app.core.domain.usecase.LogSessionEventUseCase
import com.lockin.app.core.security.BiometricHelper
import com.lockin.app.core.security.BiometricResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Enumeration representing the steps in the early break friction gate.
 */
enum class BreakStep {
    WARNING,      // Step 1: Force warning timer
    BIOMETRIC,    // Step 2: Strong biometric authentication
    CONFIRMATION  // Step 3: Typed text validation
}

/**
 * UI State definition for the early break friction gate.
 */
data class BreakGateUiState(
    val currentStep: BreakStep = BreakStep.WARNING,
    val warningSecondsLeft: Int = 10,
    val penaltyAmountPaise: Int = 0,
    val confirmationText: String = "",
    val isBreaking: Boolean = false,
    val error: String? = null,
    val isBreakSuccess: Boolean = false
)

/**
 * ViewModel processing early detox exits.
 */
@HiltViewModel
class BreakGateViewModel @Inject constructor(
    private val breakSessionUseCase: BreakSessionUseCase,
    private val logSessionEventUseCase: LogSessionEventUseCase,
    private val biometricHelper: BiometricHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(BreakGateUiState())
    val uiState: StateFlow<BreakGateUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var sessionId: String = ""

    /**
     * Initializes the viewmodel with current active session data and schedules warning timers.
     *
     * @param sessionId Active session ID.
     * @param penaltyAmountPaise Held penalty size in paise.
     */
    fun initSession(sessionId: String, penaltyAmountPaise: Int) {
        this.sessionId = sessionId
        _uiState.update {
            it.copy(
                penaltyAmountPaise = penaltyAmountPaise,
                currentStep = BreakStep.WARNING,
                warningSecondsLeft = 10,
                error = null,
                confirmationText = "",
                isBreaking = false,
                isBreakSuccess = false
            )
        }
        startWarningTimer()
    }

    /**
     * Ticks a 10-second countdown before enabling the proceed CTA in Step 1.
     */
    private fun startWarningTimer() {
        timerJob?.cancel()
        _uiState.update { it.copy(warningSecondsLeft = 10) }
        
        timerJob = viewModelScope.launch {
            try {
                logSessionEventUseCase(
                    sessionId = sessionId,
                    eventType = "BREAK_ATTEMPT",
                    metadata = "User entered early-break warning gate step."
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to log BREAK_ATTEMPT session event")
            }

            while (_uiState.value.warningSecondsLeft > 0) {
                delay(1000)
                _uiState.update { it.copy(warningSecondsLeft = it.warningSecondsLeft - 1) }
            }
        }
    }

    /**
     * Transitions step from Warning to Biometric prompt.
     */
    fun moveToBiometric() {
        if (_uiState.value.warningSecondsLeft <= 0) {
            _uiState.update {
                it.copy(
                    currentStep = BreakStep.BIOMETRIC,
                    error = null
                )
            }
        }
    }

    /**
     * Helper check to verify if biometric scanner is enrolled/available.
     */
    fun isBiometricAvailable(): Boolean {
        return biometricHelper.isBiometricAvailable()
    }

    /**
     * Initiates biometric prompt using the helper and host activity.
     */
    fun triggerBiometricPrompt(activity: FragmentActivity) {
        viewModelScope.launch {
            biometricHelper.authenticate(
                activity = activity,
                title = "Confirm Early Exit",
                subtitle = "Confirm early exit penalty charge of ₹${_uiState.value.penaltyAmountPaise / 100}"
            ).collect { result ->
                when (result) {
                    is BiometricResult.Success -> {
                        onBiometricSuccess()
                    }
                    is BiometricResult.Failure -> {
                        // Single attempt failure (e.g. bad print scan).
                        // Keep prompt open, standard Android BiometricPrompt controls this.
                    }
                    is BiometricResult.Error -> {
                        // Terminal failure / cancellation
                        onBiometricFailureOrCancel(result.errorMessage)
                    }
                }
            }
        }
    }

    /**
     * Callback when biometric check succeeds.
     */
    fun onBiometricSuccess() {
        Timber.i("Biometric step verified. Moving to Step 3 typed confirmation.")
        _uiState.update {
            it.copy(
                currentStep = BreakStep.CONFIRMATION,
                error = null
            )
        }
    }

    /**
     * Callback on biometric failure or cancel.
     * As requested, immediately redirects the user back to the Warning step, resetting the 10-second wait.
     */
    fun onBiometricFailureOrCancel(errorMessage: String) {
        Timber.w("Biometric gate cancelled/failed: $errorMessage. Sending back to warning screen.")
        
        viewModelScope.launch {
            try {
                logSessionEventUseCase(
                    sessionId = sessionId,
                    eventType = "BREAK_ATTEMPT_FAILED",
                    metadata = "Biometric check failed/cancelled: $errorMessage"
                )
            } catch (e: Exception) {
                Timber.e(e)
            }
        }

        // Kick back to Step 1 Warning
        _uiState.update {
            it.copy(
                currentStep = BreakStep.WARNING,
                error = "Biometric authentication failed. Wait 10 seconds to retry."
            )
        }
        startWarningTimer()
    }

    /**
     * Updates typed confirmation text in Step 3.
     */
    fun updateConfirmationText(text: String) {
        _uiState.update { it.copy(confirmationText = text) }
    }

    /**
     * Executes the final break early action. Checks if the match word "BREAK" was typed.
     */
    fun confirmBreak() {
        val state = _uiState.value
        if (state.confirmationText != "BREAK") {
            _uiState.update { it.copy(error = "Type the word BREAK exactly (all-caps) to proceed.") }
            return
        }

        _uiState.update { it.copy(isBreaking = true, error = null) }

        viewModelScope.launch {
            try {
                val result = breakSessionUseCase()
                if (result.isSuccess) {
                    Timber.i("Focus session successfully broken early.")
                    _uiState.update {
                        it.copy(
                            isBreaking = false,
                            isBreakSuccess = true
                        )
                    }
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Break early transaction failed"
                    Timber.e("Error breaking session: $errorMsg")
                    _uiState.update {
                        it.copy(
                            isBreaking = false,
                            error = errorMsg
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception invoking BreakSessionUseCase")
                _uiState.update {
                    it.copy(
                        isBreaking = false,
                        error = e.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

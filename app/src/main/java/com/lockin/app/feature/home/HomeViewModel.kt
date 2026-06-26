/*
 * File: com/lockin/app/feature/home/HomeViewModel.kt
 * Purpose: ViewModel for the Home dashboard.
 * Manages balance display, streak tracking, duration and penalty pickers,
 * and starts new focus sessions, handling silent Auto Top-Up triggers if needed.
 */

package com.lockin.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lockin.app.core.domain.model.Wallet
import com.lockin.app.core.domain.usecase.AutoTopUpUseCase
import com.lockin.app.core.domain.usecase.GetAutoTopUpConfigUseCase
import com.lockin.app.core.domain.usecase.GetStreakUseCase
import com.lockin.app.core.domain.usecase.GetWalletUseCase
import com.lockin.app.core.domain.usecase.StartSessionUseCase
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
 * UI state representation for the Home screen dashboard.
 *
 * @param wallet User's wallet model holding available and locked balance info.
 * @param isWalletLoading Indicator for active wallet database loading.
 * @param streakCount The current completed consecutive daily session counts.
 * @param isAutoTopUpEnabled Configuration toggle representing if Auto Top-Up is active.
 * @param isDeviceRooted Flag indicating if device integrity check failed on launch.
 * @param selectedDurationMinutes Detox duration selection (defaults to 60m).
 * @param selectedPenaltyPaise Detox stake penalty size (defaults to ₹100 = 10000 Paise).
 * @param isStartSessionProcessing True when the session startup transaction or auto top-up is executing.
 * @param startSessionError Diagnostic error message if starting the session fails.
 * @param startSessionSuccessId The successfully created focus session ID to trigger navigation.
 */
data class HomeUiState(
    val wallet: Wallet? = null,
    val isWalletLoading: Boolean = true,
    val streakCount: Int = 0,
    val isAutoTopUpEnabled: Boolean = false,
    val isDeviceRooted: Boolean = false,
    val selectedDurationMinutes: Long = 60L,
    val selectedPenaltyPaise: Int = 10000,
    val isStartSessionProcessing: Boolean = false,
    val startSessionError: String? = null,
    val startSessionSuccessId: String? = null
)

/**
 * ViewModel that processes interactions and configurations on the main LockIn Home dashboard.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getWalletUseCase: GetWalletUseCase,
    private val getStreakUseCase: GetStreakUseCase,
    private val getAutoTopUpConfigUseCase: GetAutoTopUpConfigUseCase,
    private val startSessionUseCase: StartSessionUseCase,
    private val autoTopUpUseCase: AutoTopUpUseCase,
    private val encryptedPrefsManager: EncryptedPrefsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    /**
     * Initializes state bindings, streaming wallet updates, streak changes, and reading secure configs.
     */
    private fun loadDashboardData() {
        val userId = encryptedPrefsManager.getUserId() ?: "default_user"
        val isRooted = encryptedPrefsManager.isDeviceRooted()
        val autoTopUpConfig = getAutoTopUpConfigUseCase()

        _uiState.update {
            it.copy(
                isDeviceRooted = isRooted,
                isAutoTopUpEnabled = autoTopUpConfig.autoTopUpEnabled
            )
        }

        // Stream reactive changes from Room database for the user wallet balance
        viewModelScope.launch {
            getWalletUseCase(userId).collectLatest { wallet ->
                _uiState.update {
                    it.copy(
                        wallet = wallet,
                        isWalletLoading = false
                    )
                }
            }
        }

        // Stream daily completion streaks reactively
        viewModelScope.launch {
            try {
                getStreakUseCase().collectLatest { streak ->
                    _uiState.update { it.copy(streakCount = streak) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to observe daily completion streak flow")
            }
        }
    }

    /**
     * Configures the selected focus mode duration.
     *
     * @param minutes Selected duration in minutes.
     */
    fun updateDuration(minutes: Long) {
        _uiState.update { it.copy(selectedDurationMinutes = minutes) }
    }

    /**
     * Configures the selected penalty amount.
     *
     * @param paise Selected penalty amount in Paise.
     */
    fun updatePenalty(paise: Int) {
        _uiState.update { it.copy(selectedPenaltyPaise = paise) }
    }

    /**
     * Resets navigation success or error values.
     */
    fun clearStartSessionState() {
        _uiState.update {
            it.copy(
                startSessionSuccessId = null,
                startSessionError = null
            )
        }
    }

    /**
     * Handles the 'Lock In' button click.
     * If balance is insufficient and auto top-up is enabled, triggers silent charge first.
     * Otherwise attempts to start the session immediately.
     */
    fun onLockInClicked() {
        val available = _uiState.value.wallet?.availableBalance ?: 0
        val penalty = _uiState.value.selectedPenaltyPaise
        val autoTopUpOn = _uiState.value.isAutoTopUpEnabled

        if (available < penalty) {
            if (autoTopUpOn) {
                triggerAutoTopUpAndStartSession()
            } else {
                Timber.w("Lock In aborted: insufficient balance and auto top-up disabled.")
                _uiState.update {
                    it.copy(
                        startSessionError = "Insufficient wallet balance. Please add funds."
                    )
                }
            }
        } else {
            startSession()
        }
    }

    /**
     * Executes the silent auto top-up flow in the background before proceeding to session creation.
     */
    private fun triggerAutoTopUpAndStartSession() {
        val userId = encryptedPrefsManager.getUserId() ?: return
        _uiState.update {
            it.copy(
                isStartSessionProcessing = true,
                startSessionError = "Topping up wallet automatically..."
            )
        }

        viewModelScope.launch {
            val topUpResult = autoTopUpUseCase(userId)
            if (topUpResult.isSuccess) {
                Timber.d("Auto top-up succeeded. Proceeding to start session.")
                startSession()
            } else {
                val errorMsg = topUpResult.exceptionOrNull()?.message ?: "Auto top-up payment failed"
                Timber.e("Auto top-up failed: $errorMsg")
                _uiState.update {
                    it.copy(
                        isStartSessionProcessing = false,
                        startSessionError = "Auto top-up failed: $errorMsg. Add money manually."
                    )
                }
            }
        }
    }

    /**
     * Attempts to build and execute a new focus session.
     * Deducts the held penalty, starts the background service, and navigates on database commit success.
     */
    fun startSession() {
        val userId = encryptedPrefsManager.getUserId() ?: return
        val durationMinutes = _uiState.value.selectedDurationMinutes
        val penaltyAmountPaise = _uiState.value.selectedPenaltyPaise

        _uiState.update {
            it.copy(
                isStartSessionProcessing = true,
                startSessionError = null
            )
        }

        viewModelScope.launch {
            val result = startSessionUseCase(
                userId = userId,
                penaltyAmount = penaltyAmountPaise,
                durationMs = durationMinutes * 60 * 1000L,
                allowlistVersion = 1 // Default allowlist version 1
            )
            if (result.isSuccess) {
                val session = result.getOrThrow()
                Timber.d("StartSessionUseCase commit successful: session ${session.sessionId}")
                _uiState.update {
                    it.copy(
                        isStartSessionProcessing = false,
                        startSessionSuccessId = session.sessionId
                    )
                }
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to initialize session"
                Timber.e("StartSessionUseCase error: $errorMsg")
                _uiState.update {
                    it.copy(
                        isStartSessionProcessing = false,
                        startSessionError = errorMsg
                    )
                }
            }
        }
    }
}

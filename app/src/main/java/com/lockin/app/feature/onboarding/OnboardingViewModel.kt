/*
 * File: app/src/main/java/com/lockin/app/feature/onboarding/OnboardingViewModel.kt
 * Purpose: ViewModel for the Onboarding screen flow.
 * Coordinates user onboarding steps, Google authentication, permissions states, deposit processing, and auto top-up config.
 */

package com.lockin.app.feature.onboarding

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lockin.app.core.data.payment.RazorpayManager
import com.lockin.app.core.domain.model.AutoTopUpConfig
import com.lockin.app.core.domain.model.TransactionType
import com.lockin.app.core.domain.model.WalletTransaction
import com.lockin.app.core.domain.usecase.DepositToWalletUseCase
import com.lockin.app.core.domain.usecase.SaveAutoTopUpConfigUseCase
import com.lockin.app.core.domain.usecase.SignInWithGoogleUseCase
import com.lockin.app.core.security.EncryptedPrefsManager
import com.lockin.app.feature.auth.GoogleSignInManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * UI State for the Onboarding screen flow.
 *
 * @param currentStep The current step index (0-7). Step 0 is Google Sign-in.
 * @param isGoogleSignInLoading Spinner state for Google authentication.
 * @param isGoogleSignInSuccess Success flag for Google authentication.
 * @param googleSignInError Description of Google sign-in failure.
 * @param isVpnPermissionGranted State representing if local VPN permission is granted.
 * @param isNotificationPermissionGranted State representing if notification permission is granted.
 * @param depositAmountPaise The chosen initial wallet deposit size.
 * @param isDepositProcessing Processing state for wallet deposits checkout.
 * @param isDepositSuccess Flag marking successful deposit completion.
 * @param depositError Diagnostic message if wallet deposit processing fails.
 * @param autoTopUpEnabled Auto Top-Up feature toggle.
 * @param autoTopUpThresholdPaise Threshold value under which Auto Top-Up charges are fired.
 * @param autoTopUpAmountPaise Custom size of Auto Top-Up charge transactions.
 * @param isCompleted Marks if final step is finished to proceed to main dashboard.
 * @param isExistingUser True if the user profile already exists in the system database.
 */
data class OnboardingUiState(
    val currentStep: Int = 0, // Starts at Step 0 for Google Sign-In
    val isGoogleSignInLoading: Boolean = false,
    val isGoogleSignInSuccess: Boolean = false,
    val googleSignInError: String? = null,
    val isVpnPermissionGranted: Boolean = false,
    val isNotificationPermissionGranted: Boolean = false,
    val depositAmountPaise: Int = 10000, // Default ₹100 = 10000 Paise
    val isDepositProcessing: Boolean = false,
    val isDepositSuccess: Boolean = false,
    val depositError: String? = null,
    val autoTopUpEnabled: Boolean = true,
    val autoTopUpThresholdPaise: Int = 20000, // Default ₹200
    val autoTopUpAmountPaise: Int = 50000, // Default ₹500
    val isCompleted: Boolean = false,
    val isExistingUser: Boolean = false
)

/**
 * ViewModel to track and coordinate user inputs and setup throughout the 8 onboarding steps (0-7).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val encryptedPrefsManager: EncryptedPrefsManager,
    private val saveAutoTopUpConfigUseCase: SaveAutoTopUpConfigUseCase,
    private val depositToWalletUseCase: DepositToWalletUseCase,
    private val razorpayManager: RazorpayManager,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val googleSignInManager: GoogleSignInManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    /**
     * Attempts Google Sign-In using Credential Manager and verifies credentials with the backend.
     * Why: Authenticates the user securely before granting wallet or session setup.
     *
     * @param activityContext Must be the activity context required to present Google dialogs.
     */
    fun signInWithGoogle(activityContext: Context) {
        _uiState.update { it.copy(isGoogleSignInLoading = true, googleSignInError = null) }
        viewModelScope.launch {
            val credentialResult = googleSignInManager.signIn(activityContext)
            credentialResult.fold(
                onSuccess = { idToken ->
                    // Verify Google ID token against backend /auth/google endpoint
                    val verificationResult = signInWithGoogleUseCase(idToken)
                    verificationResult.fold(
                        onSuccess = { response ->
                            Timber.d("Google Sign-In succeeded for user ID: ${response.userId}, isExistingUser: ${response.isExistingUser}")
                            if (response.isExistingUser) {
                                // Existing user: show first 5 onboarding screens (Step 0 to Step 4)
                                _uiState.update {
                                    it.copy(
                                        isGoogleSignInLoading = false,
                                        isGoogleSignInSuccess = true,
                                        isExistingUser = true,
                                        currentStep = 1 // Progress to concept screen (Step 1)
                                    )
                                }
                            } else {
                                // New user: progress through onboarding steps
                                _uiState.update {
                                    it.copy(
                                        isGoogleSignInLoading = false,
                                        isGoogleSignInSuccess = true,
                                        isExistingUser = false,
                                        currentStep = 1 // Progress directly to concept screen (Step 1)
                                    )
                                }
                            }
                        },
                        onFailure = { error ->
                            val errorMsg = error.localizedMessage ?: "Backend verification failed"
                            Timber.e(error, "Google Sign-In backend verification failed.")
                            _uiState.update {
                                it.copy(
                                    isGoogleSignInLoading = false,
                                    googleSignInError = errorMsg
                                )
                            }
                        }
                    )
                },
                onFailure = { error ->
                    val errorMsg = error.localizedMessage ?: "Google Sign-In failed"
                    Timber.e(error, "Google Sign-In authentication failed.")
                    _uiState.update {
                        it.copy(
                            isGoogleSignInLoading = false,
                            googleSignInError = errorMsg
                        )
                    }
                }
            )
        }
    }

    /**
     * Increments the onboarding step up to the maximum step limit (7).
     * If the user is an existing user and they completed Step 4 (the 5th screen), we finish onboarding.
     */
    fun nextStep() {
        val currentState = _uiState.value
        if (currentState.isExistingUser && currentState.currentStep == 4) {
            completeOnboardingForExistingUser()
            return
        }
        _uiState.update { state ->
            val next = state.currentStep + 1
            if (next <= 7) {
                state.copy(currentStep = next)
            } else {
                state
            }
        }
    }

    /**
     * Completes the onboarding walkthrough for existing users, persisting the completion flag,
     * and setting [OnboardingUiState.isCompleted] to trigger Home screen navigation.
     * Why: Prevents existing users from being forced to fund or top-up their wallets again.
     */
    private fun completeOnboardingForExistingUser() {
        viewModelScope.launch {
            try {
                // Persist onboarding completion status (Task 7.10)
                encryptedPrefsManager.saveOnboardingComplete(true)
                Timber.d("Onboarding completed for existing user after showing the first 5 onboarding screens.")
                _uiState.update { it.copy(isCompleted = true) }
            } catch (e: Exception) {
                Timber.e(e, "Error completing onboarding for existing user.")
                // Fail-safe: complete onboarding to prevent trapping user
                encryptedPrefsManager.saveOnboardingComplete(true)
                _uiState.update { it.copy(isCompleted = true) }
            }
        }
    }

    /**
     * Decrements the onboarding step down to step 1.
     * Why: Users can navigate backward down to Step 1 but are not permitted to go back to the Sign-In gate (Step 0) once authenticated.
     */
    fun previousStep() {
        _uiState.update { state ->
            val prev = state.currentStep - 1
            if (prev >= 1) {
                state.copy(currentStep = prev)
            } else {
                state
            }
        }
    }

    /**
     * Updates the status of VPN permission authorization.
     */
    fun setVpnPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(isVpnPermissionGranted = granted) }
    }

    /**
     * Updates the status of Notification permission authorization.
     */
    fun setNotificationPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(isNotificationPermissionGranted = granted) }
    }

    /**
     * Configures the amount the user wants to deposit in their initial transaction.
     */
    fun updateDepositAmount(amountPaise: Int) {
        _uiState.update { it.copy(depositAmountPaise = amountPaise) }
    }

    /**
     * Triggers the real Razorpay checkout overlay using the SDK helper wrapper.
     *
     * @param activity The host activity.
     */
    fun startDeposit(activity: Activity) {
        val amount = _uiState.value.depositAmountPaise
        if (amount <= 0) return

        _uiState.update { it.copy(isDepositProcessing = true, depositError = null) }
        viewModelScope.launch {
            val result = razorpayManager.deposit(activity, amount)
            if (result.isSuccess) {
                handleDepositSuccess(result.getOrThrow())
            } else {
                handleDepositFailure(result.exceptionOrNull()?.message ?: "Payment aborted")
            }
        }
    }

    /**
     * Callback for a successful Razorpay transaction.
     * Generates a local user ID on initial launch, credits the wallet, and saves the payment token.
     *
     * @param paymentId The Razorpay payment identifier.
     */
    fun handleDepositSuccess(paymentId: String) {
        viewModelScope.launch {
            try {
                // Save payment token securely in EncryptedSharedPreferences
                encryptedPrefsManager.saveToken(paymentId)
                
                // Initialize user ID on first successful deposit if not set
                var userId = encryptedPrefsManager.getUserId()
                if (userId == null) {
                    userId = "usr_${System.currentTimeMillis()}"
                    encryptedPrefsManager.saveUserId(userId)
                }

                // Add funds to wallet via DepositToWalletUseCase
                val result = depositToWalletUseCase(
                    userId = userId,
                    amountPaise = _uiState.value.depositAmountPaise,
                    transactionType = TransactionType.DEPOSIT,
                    razorpayPaymentId = paymentId
                )

                if (result.isSuccess) {
                    Timber.d("Initial deposit of ${_uiState.value.depositAmountPaise} paise succeeded for $userId.")
                    _uiState.update {
                        it.copy(
                            isDepositProcessing = false,
                            isDepositSuccess = true
                        )
                    }
                    nextStep()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Wallet credit failed"
                    Timber.e("Initial deposit failed to update local wallet: $errorMsg")
                    _uiState.update {
                        it.copy(
                            isDepositProcessing = false,
                            depositError = errorMsg
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error handling deposit success flow.")
                _uiState.update {
                    it.copy(
                        isDepositProcessing = false,
                        depositError = e.message ?: "Failed to process deposit success."
                    )
                }
            }
        }
    }

    /**
     * Callback when a Razorpay transaction fails.
     *
     * @param errorMessage Description of why payment failed.
     */
    fun handleDepositFailure(errorMessage: String) {
        Timber.e("Razorpay payment checkout failed: $errorMessage")
        _uiState.update {
            it.copy(
                isDepositProcessing = false,
                depositError = errorMessage
            )
        }
    }

    /**
     * Toggles whether silent Auto Top-Up is enabled.
     */
    fun toggleAutoTopUp(enabled: Boolean) {
        _uiState.update { it.copy(autoTopUpEnabled = enabled) }
    }

    /**
     * Adjusts the trigger threshold amount (in Paise) for Auto Top-Up.
     */
    fun updateAutoTopUpThreshold(thresholdPaise: Int) {
        _uiState.update { it.copy(autoTopUpThresholdPaise = thresholdPaise) }
    }

    /**
     * Adjusts the top-up transaction size (in Paise).
     */
    fun updateAutoTopUpAmount(amountPaise: Int) {
        _uiState.update { it.copy(autoTopUpAmountPaise = amountPaise) }
    }

    /**
     * Saves the final auto top-up configs, persists the onboarding completed flag,
     * and sets [OnboardingUiState.isCompleted] to trigger Home screen navigation.
     */
    fun saveAutoTopUpConfigAndComplete() {
        viewModelScope.launch {
            try {
                val token = encryptedPrefsManager.getToken()
                val config = AutoTopUpConfig(
                    autoTopUpEnabled = _uiState.value.autoTopUpEnabled,
                    autoTopUpThresholdPaise = _uiState.value.autoTopUpThresholdPaise,
                    autoTopUpAmountPaise = _uiState.value.autoTopUpAmountPaise,
                    savedPaymentMethodLabel = if (token != null) "Razorpay Saved Instrument" else null
                )
                saveAutoTopUpConfigUseCase(config, token)
                
                // Persist onboarding completion status (Task 7.10)
                encryptedPrefsManager.saveOnboardingComplete(true)
                
                Timber.d("Onboarding completed and config saved successfully.")
                _uiState.update { it.copy(isCompleted = true) }
            } catch (e: Exception) {
                Timber.e(e, "Error saving onboarding configurations.")
                // Fail-safe: complete onboarding to prevent trapping user
                encryptedPrefsManager.saveOnboardingComplete(true)
                _uiState.update { it.copy(isCompleted = true) }
            }
        }
    }
}

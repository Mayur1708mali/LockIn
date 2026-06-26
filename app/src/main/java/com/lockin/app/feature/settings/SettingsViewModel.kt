/*
 * File: app/src/main/java/com/lockin/app/feature/settings/SettingsViewModel.kt
 * Purpose: ViewModel for the Settings feature of LockIn.
 * Exposes the UI state representing the allowlist (default + custom),
 * auto top-up configs, active session gating, and handles settings modifications.
 */

package com.lockin.app.feature.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.app.Activity
import com.lockin.app.core.data.payment.RazorpayManager
import com.lockin.app.core.domain.model.AllowedApp
import com.lockin.app.core.domain.repository.AllowedAppRepository
import com.lockin.app.core.domain.repository.SessionRepository
import com.lockin.app.core.domain.repository.WalletRepository
import com.lockin.app.core.security.EncryptedPrefsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Data representation of a default allowlisted system app.
 */
data class DefaultAppInfo(
    val packageName: String,
    val appName: String
)

/**
 * Data representation of an installed launcher application on the device.
 */
data class InstalledAppInfo(
    val packageName: String,
    val appName: String
)

/**
 * Combined data payload for the successful settings state.
 */
data class SettingsUiStateData(
    val customAllowedApps: List<AllowedApp> = emptyList(),
    val defaultAllowedApps: List<DefaultAppInfo> = emptyList(),
    val autoTopUpEnabled: Boolean = true,
    val autoTopUpThresholdPaise: Int = 20000,
    val autoTopUpAmountPaise: Int = 50000,
    val savedPaymentMethodLabel: String? = null,
    val isSessionActive: Boolean = false,
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val userId: String = "default_user"
)

/**
 * Sealed UI State interface following single source of truth guidelines.
 */
sealed interface SettingsUiState {
    object Loading : SettingsUiState
    data class Success(val data: SettingsUiStateData) : SettingsUiState
    data class Error(val message: String) : SettingsUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val allowedAppRepository: AllowedAppRepository,
    private val sessionRepository: SessionRepository,
    private val walletRepository: WalletRepository,
    private val encryptedPrefsManager: EncryptedPrefsManager,
    private val razorpayManager: RazorpayManager
) : ViewModel() {

    private val userId = encryptedPrefsManager.getUserId() ?: "default_user"
    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())

    // Combine all domain streams to formulate a reactive UI state
    val uiState: StateFlow<SettingsUiState> = combine(
        allowedAppRepository.getAllAllowedAppsFlow(),
        sessionRepository.getActiveSessionFlow(),
        walletRepository.getWalletFlow(userId),
        _installedApps
    ) { customApps, activeSession, wallet, installedList ->
        try {
            val config = encryptedPrefsManager.getAutoTopUpConfig()
            val defaultApps = listOf(
                DefaultAppInfo("com.google.android.apps.nbu.paisa.user", "Google Pay"),
                DefaultAppInfo("com.phonepe.app", "PhonePe"),
                DefaultAppInfo("net.one97.communications", "Paytm"),
                DefaultAppInfo("in.org.npci.upiapp", "BHIM UPI"),
                DefaultAppInfo("com.android.emergency", "Emergency services")
            )
            
            val data = SettingsUiStateData(
                customAllowedApps = customApps,
                defaultAllowedApps = defaultApps,
                autoTopUpEnabled = wallet?.autoTopUpEnabled ?: config.autoTopUpEnabled,
                autoTopUpThresholdPaise = wallet?.autoTopUpThresholdPaise ?: config.autoTopUpThresholdPaise,
                autoTopUpAmountPaise = wallet?.autoTopUpAmountPaise ?: config.autoTopUpAmountPaise,
                savedPaymentMethodLabel = config.savedPaymentMethodLabel ?: "Razorpay Saved Instrument",
                isSessionActive = activeSession != null,
                installedApps = installedList,
                userId = userId
            )
            SettingsUiState.Success(data)
        } catch (e: Exception) {
            Timber.e(e, "Error combining settings UI state flows")
            SettingsUiState.Error(e.message ?: "Failed to load settings configuration")
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState.Loading
    )

    init {
        loadInstalledLauncherApps()
    }

    /**
     * Resolves and filters the installed launcher applications on the host operating system.
     * Runs in a background thread to prevent UI freezing.
     */
    private fun loadInstalledLauncherApps() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val activities = pm.queryIntentActivities(intent, 0)
                val apps = activities.mapNotNull { resolveInfo ->
                    val packageName = resolveInfo.activityInfo.packageName
                    val appName = resolveInfo.loadLabel(pm).toString()
                    if (packageName != context.packageName) {
                        InstalledAppInfo(packageName, appName)
                    } else {
                        null
                    }
                }.distinctBy { it.packageName }
                 .sortedBy { it.appName.lowercase() }

                _installedApps.value = apps
            } catch (e: Exception) {
                Timber.e(e, "Failed to load installed launcher activities.")
            }
        }
    }

    /**
     * Updates the status of the auto top-up toggle in local database and encrypted preferences.
     *
     * @param enabled The new setting state.
     */
    fun toggleAutoTopUp(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val wallet = walletRepository.getWallet(userId)
                if (wallet != null) {
                    val updatedWallet = wallet.copy(
                        autoTopUpEnabled = enabled,
                        lastUpdated = System.currentTimeMillis()
                    )
                    walletRepository.updateWallet(updatedWallet)
                }
                val currentConfig = encryptedPrefsManager.getAutoTopUpConfig()
                encryptedPrefsManager.saveAutoTopUpConfig(
                    currentConfig.copy(autoTopUpEnabled = enabled)
                )
                Timber.d("Auto top-up enabled status updated: %b", enabled)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update auto top-up enabled toggle")
            }
        }
    }

    /**
     * Updates the auto top-up trigger balance threshold.
     *
     * @param thresholdPaise The configured threshold in paise.
     */
    fun updateAutoTopUpThreshold(thresholdPaise: Int) {
        viewModelScope.launch {
            try {
                val wallet = walletRepository.getWallet(userId)
                if (wallet != null) {
                    val updatedWallet = wallet.copy(
                        autoTopUpThresholdPaise = thresholdPaise,
                        lastUpdated = System.currentTimeMillis()
                    )
                    walletRepository.updateWallet(updatedWallet)
                }
                val currentConfig = encryptedPrefsManager.getAutoTopUpConfig()
                encryptedPrefsManager.saveAutoTopUpConfig(
                    currentConfig.copy(autoTopUpThresholdPaise = thresholdPaise)
                )
                Timber.d("Auto top-up threshold updated: %d paise", thresholdPaise)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update auto top-up threshold configuration")
            }
        }
    }

    /**
     * Updates the amount charged to the user's account when auto top-up is triggered.
     *
     * @param amountPaise The configuration amount in paise.
     */
    fun updateAutoTopUpAmount(amountPaise: Int) {
        viewModelScope.launch {
            try {
                val wallet = walletRepository.getWallet(userId)
                if (wallet != null) {
                    val updatedWallet = wallet.copy(
                        autoTopUpAmountPaise = amountPaise,
                        lastUpdated = System.currentTimeMillis()
                    )
                    walletRepository.updateWallet(updatedWallet)
                }
                val currentConfig = encryptedPrefsManager.getAutoTopUpConfig()
                encryptedPrefsManager.saveAutoTopUpConfig(
                    currentConfig.copy(autoTopUpAmountPaise = amountPaise)
                )
                Timber.d("Auto top-up amount updated: %d paise", amountPaise)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update auto top-up amount configuration")
            }
        }
    }

    /**
     * Adds an application package name and description to the custom user allowlist.
     * Checks first that the current custom allowed app count is below the limit of 3.
     *
     * @param packageName The application identifier package.
     * @param appName The display app name.
     */
    fun addAllowedApp(packageName: String, appName: String) {
        viewModelScope.launch {
            try {
                val currentApps = allowedAppRepository.getAllAllowedApps()
                if (currentApps.size >= 3) {
                    Timber.w("Cannot add app: custom allowlist limit of 3 reached.")
                    return@launch
                }
                val success = allowedAppRepository.addAllowedApp(packageName, appName)
                if (success) {
                    Timber.i("Successfully added app %s to custom allowlist.", packageName)
                } else {
                    Timber.w("Failed to add app %s to custom allowlist.", packageName)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error adding app to custom allowlist")
            }
        }
    }

    /**
     * Deletes an app package from the custom user allowlist.
     *
     * @param packageName The package identifier to remove.
     */
    fun removeAllowedApp(packageName: String) {
        viewModelScope.launch {
            try {
                val success = allowedAppRepository.removeAllowedApp(packageName)
                if (success) {
                    Timber.i("Successfully removed app %s from custom allowlist.", packageName)
                } else {
                    Timber.w("Failed to remove app %s from custom allowlist.", packageName)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error removing app from custom allowlist")
            }
        }
    }

    /**
     * Launches a Razorpay checkout for a minimum ₹50 transaction to authorize and save a new payment token.
     * On success, updates the stored payment method details in the configurations.
     *
     * @param activity The host Activity to launch the Razorpay overlay from.
     * @param onSuccess Callback executed when payment succeeds.
     * @param onFailure Callback executed when payment fails.
     */
    fun changePaymentMethod(
        activity: Activity,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                // Minimum deposit of ₹50 to register/verify the new instrument token
                val result = razorpayManager.deposit(activity, 5000)
                if (result.isSuccess) {
                    val paymentId = result.getOrThrow()
                    // The token is automatically saved inside razorpayManager.onPaymentSuccess(paymentId)
                    // Let's update the human-readable label
                    val currentConfig = encryptedPrefsManager.getAutoTopUpConfig()
                    val truncatedId = if (paymentId.length > 4) paymentId.takeLast(4) else paymentId
                    encryptedPrefsManager.saveAutoTopUpConfig(
                        currentConfig.copy(savedPaymentMethodLabel = "Razorpay (··· $truncatedId)")
                    )
                    Timber.i("Payment method changed successfully. Token: %s", paymentId)
                    onSuccess()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Verification payment failed"
                    Timber.e("Failed to change payment method: %s", errorMsg)
                    onFailure(errorMsg)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error changing payment method")
                onFailure(e.message ?: "Internal error changing payment method")
            }
        }
    }

    /**
     * Resets onboarding status and deletes local user credentials from the device.
     */
    fun deleteAccountLocal() {
        viewModelScope.launch {
            try {
                // Clear secure preferences
                encryptedPrefsManager.saveOnboardingComplete(false)
                encryptedPrefsManager.saveToken("")
                encryptedPrefsManager.saveUserId("")
                Timber.i("Local account data and onboarding status successfully cleared.")
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear account data locally.")
            }
        }
    }
}

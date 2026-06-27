/*
 * File: app/src/main/java/com/lockin/app/service/AutoTopUpService.kt
 * Purpose: WorkManager periodic worker that handles auto wallet top-ups.
 * Invoked by WorkManager scheduler to check balance, verify limits, perform
 * silent Razorpay charges, and notify the user of transaction results.
 */

package com.lockin.app.service

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lockin.app.core.domain.usecase.AutoTopUpUseCase
import com.lockin.app.core.domain.repository.WalletRepository
import com.lockin.app.core.notification.LockInNotificationManager
import com.lockin.app.core.security.EncryptedPrefsManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Background worker class that periodically triggers wallet balance verification and deposits.
 * Interacts with AutoTopUpUseCase to execute gates and payments.
 */
@HiltWorker
class AutoTopUpService @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val autoTopUpUseCase: AutoTopUpUseCase,
    private val encryptedPrefsManager: EncryptedPrefsManager,
    private val walletRepository: WalletRepository,
    private val notificationManager: LockInNotificationManager
) : CoroutineWorker(context, workerParams) {

    /**
     * Executes the background task check. Runs wallet verification and handles notifications.
     *
     * @return ListenableWorker.Result indicating success or failure.
     */
    override suspend fun doWork(): Result {
        Timber.i("Auto top-up background worker check triggered.")

        val userId = encryptedPrefsManager.getUserId() ?: "default_user"

        val result = autoTopUpUseCase(userId)

        if (result.isSuccess) {
            val wasToppedUp = result.getOrNull() ?: false
            if (wasToppedUp) {
                // Topped up successfully! Fetch the amount to show in the notification.
                val wallet = walletRepository.getWallet(userId)
                val amountText = if (wallet != null) {
                    "₹${wallet.autoTopUpAmountPaise / 100}"
                } else {
                    "₹500" // Default/fallback
                }
                notificationManager.showAutoTopUpSuccessNotification(amountText)
            }
            return Result.success()
        } else {
            val exception = result.exceptionOrNull()
            val errorMessage = exception?.message ?: "Unknown error"
            Timber.e("Auto top-up worker failed: %s", errorMessage)

            when {
                // If failure is because of an active session, skip silently without notification or disabling.
                errorMessage.contains("active focus session", ignoreCase = true) -> {
                    Timber.i("Suppressing auto top-up notification because a focus session is active.")
                }
                // If daily limit was reached, post the specific limit notification
                errorMessage.contains("limit (3) reached", ignoreCase = true) || 
                errorMessage.contains("limit reached", ignoreCase = true) -> {
                    notificationManager.showDailyCapNotification()
                }
                // Otherwise, payment/database failure -> post failure and temporarily disable auto top-up
                else -> {
                    notificationManager.showAutoTopUpFailureNotification()
                    disableAutoTopUp(userId)
                }
            }
            return Result.success() // Return success so that the system doesn't trigger retries on expected failures
        }
    }

    /**
     * Disables the auto top-up feature temporarily in the database and encrypted preferences.
     * Called when a background charge fails.
     *
     * @param userId The current user's ID.
     */
    private suspend fun disableAutoTopUp(userId: String) {
        try {
            val wallet = walletRepository.getWallet(userId)
            if (wallet != null) {
                val updatedWallet = wallet.copy(
                    autoTopUpEnabled = false,
                    lastUpdated = System.currentTimeMillis()
                )
                walletRepository.updateWallet(updatedWallet)
                // Also update stored config in EncryptedPrefsManager to sync
                val currentConfig = encryptedPrefsManager.getAutoTopUpConfig()
                encryptedPrefsManager.saveAutoTopUpConfig(
                    currentConfig.copy(autoTopUpEnabled = false)
                )
                Timber.i("Auto top-up has been disabled for user $userId due to charge failure.")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to disable auto top-up after charge failure")
        }
    }
}

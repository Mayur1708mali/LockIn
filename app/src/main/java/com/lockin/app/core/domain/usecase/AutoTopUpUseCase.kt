package com.lockin.app.core.domain.usecase

import com.lockin.app.core.data.payment.RazorpayManager
import com.lockin.app.core.domain.model.TransactionType
import com.lockin.app.core.domain.repository.SessionRepository
import com.lockin.app.core.domain.repository.WalletRepository
import com.lockin.app.core.security.EncryptedPrefsManager
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import timber.log.Timber

/**
 * Use case to automatically top up the user's wallet when their available balance falls below the threshold.
 * Enforces the daily cap of 3 top-ups and prevents top-ups during an active focus session.
 */
class AutoTopUpUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val sessionRepository: SessionRepository,
    private val depositToWalletUseCase: DepositToWalletUseCase,
    private val encryptedPrefsManager: EncryptedPrefsManager,
    private val razorpayManager: RazorpayManager
) {

    /**
     * Executes the auto top-up flow if all validation conditions are met:
     * 1. Auto top-up is enabled.
     * 2. Available balance is below the configured threshold.
     * 3. No active focus session exists.
     * 4. Daily auto top-up count is less than 3.
     *
     * @param userId The current user ID.
     * @return Result wrapping true if top-up succeeded, false if skipped, or exception if validation/payment failed.
     */
    suspend operator fun invoke(userId: String): Result<Boolean> {
        // 1. Fetch wallet
        val wallet = walletRepository.getWallet(userId)
            ?: return Result.failure(IllegalStateException("No wallet found for user $userId"))

        // 2. Check if auto top-up is enabled
        if (!wallet.autoTopUpEnabled) {
            Timber.i("Auto top-up skipped: feature is disabled for user $userId")
            return Result.success(false)
        }

        // 3. Check if balance is below threshold
        if (wallet.availableBalance >= wallet.autoTopUpThresholdPaise) {
            Timber.i("Auto top-up skipped: available balance (%d paise) is above threshold (%d paise)", 
                wallet.availableBalance, wallet.autoTopUpThresholdPaise)
            return Result.success(false)
        }

        // 4. Enforce: never trigger during active session
        val activeSession = sessionRepository.getActiveSession()
        if (activeSession != null) {
            Timber.w("Auto top-up skipped: active focus session exists.")
            return Result.failure(IllegalStateException("Cannot trigger auto top-up during an active focus session"))
        }

        // 5. Enforce: daily cap < 3
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = calendar.timeInMillis
        val topUpCountToday = walletRepository.getTransactionCountByTypeSince(
            TransactionType.AUTO_TOPUP,
            startOfToday
        )

        if (topUpCountToday >= 3) {
            Timber.w("Auto top-up skipped: daily cap of 3 reached. Current count today: %d", topUpCountToday)
            return Result.failure(IllegalStateException("Daily auto top-up limit (3) reached"))
        }

        // 6. Charge token (silently in background)
        val token = encryptedPrefsManager.getToken()
            ?: return Result.failure(IllegalStateException("No saved payment instrument token found for auto top-up"))

        val chargeResult = razorpayManager.chargeToken(userId, wallet.autoTopUpAmountPaise, token)
        if (chargeResult.isFailure) {
            return Result.failure(Exception("Silent token charge payment gateway error: ${chargeResult.exceptionOrNull()?.message}"))
        }

        val paymentTxId = "pay_auto_" + UUID.randomUUID().toString().replace("-", "").take(14)
        Timber.i("Silent token charge succeeded. Payment ID: %s", paymentTxId)

        // 7. Deposit the top-up amount to wallet and log AUTO_TOPUP transaction
        val depositResult = depositToWalletUseCase(
            userId = userId,
            amountPaise = wallet.autoTopUpAmountPaise,
            transactionType = TransactionType.AUTO_TOPUP,
            razorpayPaymentId = paymentTxId
        )

        return depositResult
    }
}

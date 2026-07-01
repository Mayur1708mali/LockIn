package com.lockin.app.core.domain.usecase

import com.lockin.app.core.data.remote.api.WalletApi
import com.lockin.app.core.data.remote.dto.DepositRequest
import com.lockin.app.core.domain.model.TransactionType
import com.lockin.app.core.domain.model.WalletTransaction
import com.lockin.app.core.domain.repository.WalletRepository
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Use case to deposit funds into the user's wallet.
 * Executed after a successful Razorpay payment callback (deposit or auto top-up).
 */
class DepositToWalletUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val walletApi: WalletApi
) {

    /**
     * Deposits an amount into the wallet. Creates the wallet first if it is the user's initial deposit.
     *
     * @param userId The current user ID.
     * @param amountPaise The amount deposited in Paise (minimum ₹50 = 5000 Paise).
     * @param transactionType Either TransactionType.DEPOSIT or TransactionType.AUTO_TOPUP.
     * @param razorpayPaymentId Optional Razorpay payment ID (for logging).
     * @param isSynced True if transaction has already been verified/recorded on the remote database.
     * @return Result of the atomic operation.
     */
    suspend operator fun invoke(
        userId: String,
        amountPaise: Int, // in paise
        transactionType: TransactionType = TransactionType.DEPOSIT,
        razorpayPaymentId: String? = null,
        isSynced: Boolean = false
    ): Result<Boolean> {
        if (amountPaise < 5000) {
            return Result.failure(IllegalArgumentException("Minimum deposit amount is ₹50 (5000 Paise)"))
        }

        val now = System.currentTimeMillis()
        val txId = razorpayPaymentId ?: UUID.randomUUID().toString()

        val description = if (transactionType == TransactionType.AUTO_TOPUP) {
            "Auto Top-Up via Razorpay method"
        } else {
            "Manual Deposit via Razorpay"
        }

        var finalSynced = isSynced
        if (transactionType == TransactionType.DEPOSIT && !isSynced) {
            // Upload transaction to backend database (Request 2)
            try {
                walletApi.deposit(
                    DepositRequest(
                        razorpayPaymentId = txId,
                        amount = amountPaise
                    )
                )
                finalSynced = true
                Timber.d("Successfully synced manual deposit transaction to server.")
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync manual deposit to server. Will retry later.")
            }
        }

        val depositTransaction = WalletTransaction(
            txId = txId,
            userId = userId,
            type = transactionType,
            amount = amountPaise,
            direction = "CREDIT",
            sessionId = null,
            description = description,
            timestamp = now,
            isSynced = finalSynced
        )

        val success = walletRepository.depositTransaction(
            userId = userId,
            amountPaise = amountPaise,
            transaction = depositTransaction
        )

        return if (success) {
            Result.success(true)
        } else {
            Result.failure(IllegalStateException("Database error executing deposit transaction"))
        }
    }
}

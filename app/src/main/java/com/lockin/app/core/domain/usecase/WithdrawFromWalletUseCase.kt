package com.lockin.app.core.domain.usecase

import com.lockin.app.core.data.remote.api.WalletApi
import com.lockin.app.core.data.remote.dto.WithdrawRequest
import com.lockin.app.core.domain.model.TransactionType
import com.lockin.app.core.domain.model.WalletTransaction
import com.lockin.app.core.domain.repository.WalletRepository
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Use case to withdraw funds from the user's available wallet balance.
 * Validates balance requirements and initiates a manual bank withdrawal request.
 */
class WithdrawFromWalletUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val walletApi: WalletApi
) {

    /**
     * Initiates a manual withdrawal of funds.
     *
     * @param userId The current user ID.
     * @param amountPaise The amount to withdraw in Paise (minimum ₹50 = 5000 Paise).
     * @return Result of the atomic operation.
     */
    suspend operator fun invoke(
        userId: String,
        amountPaise: Int
    ): Result<Boolean> {
        if (amountPaise < 5000) {
            return Result.failure(IllegalArgumentException("Minimum withdrawal amount is ₹50 (5000 Paise)"))
        }

        val wallet = walletRepository.getWallet(userId)
            ?: return Result.failure(IllegalStateException("No wallet found for user"))

        if (wallet.availableBalance < amountPaise) {
            return Result.failure(IllegalArgumentException("Insufficient available balance for withdrawal"))
        }

        val now = System.currentTimeMillis()
        val txId = UUID.randomUUID().toString()

        var initiallySynced = false
        try {
            walletApi.withdraw(WithdrawRequest(amount = amountPaise))
            initiallySynced = true
            Timber.d("Successfully synced withdrawal request on remote server.")
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync withdrawal request to server. Will retry later.")
        }

        val withdrawalTransaction = WalletTransaction(
            txId = txId,
            userId = userId,
            type = TransactionType.WITHDRAWAL,
            amount = amountPaise,
            direction = "DEBIT",
            sessionId = null,
            description = "Manual Withdrawal to Bank (3-5 days processing)",
            timestamp = now,
            isSynced = initiallySynced
        )

        val success = walletRepository.withdrawTransaction(
            userId = userId,
            amountPaise = amountPaise,
            transaction = withdrawalTransaction
        )

        return if (success) {
            Result.success(true)
        } else {
            Result.failure(IllegalStateException("Database error executing withdrawal transaction"))
        }
    }
}

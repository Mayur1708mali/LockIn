package com.lockin.app.core.domain.usecase

import com.lockin.app.core.data.remote.api.SessionApi
import com.lockin.app.core.data.remote.dto.SessionCreateRequest
import com.lockin.app.core.domain.model.Session
import com.lockin.app.core.domain.model.SessionStatus
import com.lockin.app.core.domain.model.TransactionType
import com.lockin.app.core.domain.model.WalletTransaction
import com.lockin.app.core.domain.repository.SessionRepository
import com.lockin.app.core.domain.repository.WalletRepository
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Use case to start a focus detox session.
 * Validates wallet balance, holds the penalty amount, creates the session, and logs the hold transaction atomically.
 */
class StartSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val walletRepository: WalletRepository,
    private val sessionApi: SessionApi
) {

    /**
     * Attempts to start a focus session.
     * Checks if the user's available balance is greater than or equal to the penalty amount.
     * If validated, executes a database transaction to lock the balance and save the session.
     *
     * @param userId The current user ID.
     * @param penaltyAmount The penalty stakes amount in Paise.
     * @param durationMs The session lock duration in milliseconds.
     * @param allowlistVersion The version ID of the app allowlist active at session start.
     * @return Result wrapping the active Session, or an exception on validation or database failure.
     */
    suspend operator fun invoke(
        userId: String,
        penaltyAmount: Int, // in paise
        durationMs: Long,
        allowlistVersion: Int
    ): Result<Session> {
        val now = System.currentTimeMillis()
        val targetEndTime = now + durationMs

        // 1. Fetch wallet status
        val wallet = walletRepository.getWallet(userId) ?: return Result.failure(
            IllegalStateException("Wallet not initialized for user $userId")
        )

        // 2. Validate sufficient balance
        if (wallet.availableBalance < penaltyAmount) {
            return Result.failure(
                IllegalArgumentException("Insufficient wallet balance. Required: ₹${penaltyAmount / 100}, Available: ₹${wallet.availableBalance / 100}")
            )
        }

        // 3. Generate session ID and transaction ID
        val sessionId = UUID.randomUUID().toString()
        val txHoldId = UUID.randomUUID().toString()

        var initiallySynced = false
        try {
            sessionApi.createSession(
                SessionCreateRequest(
                    sessionId = sessionId,
                    penaltyAmount = penaltyAmount,
                    startTime = now,
                    targetEndTime = targetEndTime,
                    allowlistVersion = allowlistVersion
                )
            )
            initiallySynced = true
        } catch (e: Exception) {
            Timber.e(e, "Error creating session on remote server. Failing start session.")
            return Result.failure(Exception("Cannot start session: Backend connection failed. Details: ${e.message}"))
        }

        val newSession = Session(
            sessionId = sessionId,
            userId = userId,
            status = SessionStatus.ACTIVE,
            startTime = now,
            targetEndTime = targetEndTime,
            actualEndTime = null,
            penaltyAmount = penaltyAmount,
            walletTxHoldId = txHoldId,
            allowlistVersion = allowlistVersion,
            isSynced = initiallySynced
        )

        val holdTransaction = WalletTransaction(
            txId = txHoldId,
            userId = userId,
            type = TransactionType.SESSION_HOLD,
            amount = penaltyAmount,
            direction = "DEBIT",
            sessionId = sessionId,
            description = "Hold penalty amount for active session",
            timestamp = now,
            isSynced = initiallySynced
        )

        val newAvailable = wallet.availableBalance - penaltyAmount
        val newHeld = wallet.heldBalance + penaltyAmount

        // 4. Execute startup transaction atomically
        val transactionSuccess = sessionRepository.startSessionTransaction(
            session = newSession,
            holdTransaction = holdTransaction,
            newAvailableBalance = newAvailable,
            newHeldBalance = newHeld
        )

        return if (transactionSuccess) {
            Result.success(newSession)
        } else {
            Result.failure(IllegalStateException("Database error executing start session transaction"))
        }
    }
}

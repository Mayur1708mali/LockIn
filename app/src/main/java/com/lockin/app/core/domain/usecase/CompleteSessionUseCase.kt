package com.lockin.app.core.domain.usecase

import com.lockin.app.core.domain.model.Session
import com.lockin.app.core.domain.model.SessionEvent
import com.lockin.app.core.domain.model.SessionStatus
import com.lockin.app.core.domain.model.TransactionType
import com.lockin.app.core.domain.model.WalletTransaction
import com.lockin.app.core.domain.repository.SessionRepository
import com.lockin.app.core.domain.repository.WalletRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Use case to complete an active focus session successfully.
 * Releases the locked penalty funds back to the user's available balance.
 */
class CompleteSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val walletRepository: WalletRepository
) {

    /**
     * Completes the current active session.
     * Releases held balance back into available balance, marks session as COMPLETED, and logs the release.
     *
     * @return Result containing the completed Session, or failure.
     */
    suspend operator fun invoke(): Result<Session> {
        val now = System.currentTimeMillis()

        // 1. Fetch active session
        val activeSession = sessionRepository.getActiveSession() ?: return Result.failure(
            IllegalStateException("No active focus session found to complete.")
        )

        // 2. Fetch wallet status
        val wallet = walletRepository.getWallet(activeSession.userId) ?: return Result.failure(
            IllegalStateException("Wallet not initialized for user ${activeSession.userId}")
        )

        // 3. Prepare updated models
        val completedSession = activeSession.copy(
            status = SessionStatus.COMPLETED,
            actualEndTime = now
        )

        val txReleaseId = UUID.randomUUID().toString()
        val releaseTransaction = WalletTransaction(
            txId = txReleaseId,
            userId = activeSession.userId,
            type = TransactionType.SESSION_RELEASE,
            amount = activeSession.penaltyAmount,
            direction = "CREDIT",
            sessionId = activeSession.sessionId,
            description = "Release held penalty amount upon session completion",
            timestamp = now
        )

        val eventId = UUID.randomUUID().toString()
        val completedEvent = SessionEvent(
            eventId = eventId,
            sessionId = activeSession.sessionId,
            eventType = "COMPLETED",
            timestamp = now,
            metadata = "Session completed successfully. Released: ₹${activeSession.penaltyAmount / 100}"
        )

        val newAvailable = wallet.availableBalance + activeSession.penaltyAmount
        val newHeld = wallet.heldBalance - activeSession.penaltyAmount

        // 4. Run atomic update
        val success = sessionRepository.completeSessionTransaction(
            session = completedSession,
            releaseTransaction = releaseTransaction,
            newAvailableBalance = newAvailable,
            newHeldBalance = newHeld,
            completedEvent = completedEvent
        )

        return if (success) {
            Result.success(completedSession)
        } else {
            Result.failure(IllegalStateException("Database error executing complete session transaction"))
        }
    }
}

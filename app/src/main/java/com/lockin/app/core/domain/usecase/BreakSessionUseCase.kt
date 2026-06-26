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
 * Use case to break an active focus session early (early detox break friction gate confirmation).
 * Consumes the locked penalty amount permanently.
 */
class BreakSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val walletRepository: WalletRepository
) {

    /**
     * Confirms the breaking of the active focus session.
     * Deducts held balance permanently as a penalty, marks session as BROKEN, and logs the charge transaction.
     *
     * @return Result containing the broken Session, or failure.
     */
    suspend operator fun invoke(): Result<Session> {
        val now = System.currentTimeMillis()

        // 1. Fetch active session
        val activeSession = sessionRepository.getActiveSession() ?: return Result.failure(
            IllegalStateException("No active focus session found to break.")
        )

        // 2. Fetch wallet status
        val wallet = walletRepository.getWallet(activeSession.userId) ?: return Result.failure(
            IllegalStateException("Wallet not initialized for user ${activeSession.userId}")
        )

        // 3. Prepare updated models
        val brokenSession = activeSession.copy(
            status = SessionStatus.BROKEN,
            actualEndTime = now
        )

        val txPenaltyId = UUID.randomUUID().toString()
        val penaltyTransaction = WalletTransaction(
            txId = txPenaltyId,
            userId = activeSession.userId,
            type = TransactionType.PENALTY,
            amount = activeSession.penaltyAmount,
            direction = "DEBIT",
            sessionId = activeSession.sessionId,
            description = "Charged penalty amount for breaking focus session early",
            timestamp = now
        )

        val eventId = UUID.randomUUID().toString()
        val brokenEvent = SessionEvent(
            eventId = eventId,
            sessionId = activeSession.sessionId,
            eventType = "BREAK_CONFIRMED",
            timestamp = now,
            metadata = "Session broken early. Charged: ₹${activeSession.penaltyAmount / 100}"
        )

        val newHeld = wallet.heldBalance - activeSession.penaltyAmount
        val newTotalPenalties = wallet.totalPenaltiesPaid + activeSession.penaltyAmount

        // 4. Run atomic break transaction
        val success = sessionRepository.breakSessionTransaction(
            session = brokenSession,
            penaltyTransaction = penaltyTransaction,
            newHeldBalance = newHeld,
            newTotalPenaltiesPaid = newTotalPenalties,
            brokenEvent = brokenEvent
        )

        return if (success) {
            Result.success(brokenSession)
        } else {
            Result.failure(IllegalStateException("Database error executing break session transaction"))
        }
    }
}

package com.lockin.app.core.domain.usecase

import com.lockin.app.core.data.remote.api.SessionApi
import com.lockin.app.core.data.remote.dto.SessionUpdateRequest
import com.lockin.app.core.domain.model.Session
import com.lockin.app.core.domain.model.SessionEvent
import com.lockin.app.core.domain.model.SessionStatus
import com.lockin.app.core.domain.model.TransactionType
import com.lockin.app.core.domain.model.WalletTransaction
import com.lockin.app.core.domain.repository.SessionRepository
import com.lockin.app.core.domain.repository.WalletRepository
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Use case to break an active focus session early (early detox break friction gate confirmation).
 * Consumes the locked penalty amount permanently.
 */
class BreakSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val walletRepository: WalletRepository,
    private val sessionApi: SessionApi
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
            timestamp = now,
            isSynced = false
        )

        val eventId = UUID.randomUUID().toString()
        val brokenEvent = SessionEvent(
            eventId = eventId,
            sessionId = activeSession.sessionId,
            eventType = "BREAK_CONFIRMED",
            timestamp = now,
            metadata = "Session broken early. Charged: ₹${activeSession.penaltyAmount / 100}",
            isSynced = false
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
            // Attempt to sync the session breakage to the server immediately (Request 1)
            try {
                sessionApi.updateSession(
                    sessionId = brokenSession.sessionId,
                    request = SessionUpdateRequest(
                        status = "BROKEN",
                        actualEndTime = now
                    )
                )
                // Mark session, penalty transaction, and breakage event as synced in local DB (Request 1 & 2)
                sessionRepository.markSessionSynced(brokenSession.sessionId)
                walletRepository.markTransactionSynced(penaltyTransaction.txId)
                sessionRepository.markEventSynced(brokenEvent.eventId)
                Timber.d("Successfully synced broken session to server database.")
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync broken session to server. Will retry later.")
            }
            Result.success(brokenSession)
        } else {
            Result.failure(IllegalStateException("Database error executing break session transaction"))
        }
    }
}

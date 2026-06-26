package com.lockin.app.core.domain.repository

import com.lockin.app.core.domain.model.Session
import com.lockin.app.core.domain.model.SessionEvent
import com.lockin.app.core.domain.model.WalletTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository interface for managing focus sessions and their lifecycle events.
 */
interface SessionRepository {

    /**
     * Inserts a new session record.
     */
    suspend fun insertSession(session: Session)

    /**
     * Updates an existing session record with new lifecycle information.
     */
    suspend fun updateSession(session: Session)

    /**
     * Fetches a session by its unique ID.
     */
    suspend fun getSessionById(sessionId: String): Session?

    /**
     * Streams all sessions ordered by start time.
     */
    fun getAllSessionsFlow(): Flow<List<Session>>

    /**
     * Streams the active session, if one exists.
     */
    fun getActiveSessionFlow(): Flow<Session?>

    /**
     * Non-reactive check of the active session (one-shot check).
     */
    suspend fun getActiveSession(): Session?

    /**
     * Appends a new audit event to the session logs.
     */
    suspend fun insertEvent(event: SessionEvent)

    /**
     * Fetches all audit events logged for a specific session.
     */
    suspend fun getEventsForSession(sessionId: String): List<SessionEvent>

    /**
     * Executes the session startup routine atomically.
     * Updates available and held balances, inserts the session, and logs the ledger transaction.
     */
    suspend fun startSessionTransaction(
        session: Session,
        holdTransaction: WalletTransaction,
        newAvailableBalance: Int,
        newHeldBalance: Int
    ): Boolean

    /**
     * Executes the session completion routine atomically.
     * Releases held funds back to available, updates session status to COMPLETED, and logs the transaction and event.
     */
    suspend fun completeSessionTransaction(
        session: Session,
        releaseTransaction: WalletTransaction,
        newAvailableBalance: Int,
        newHeldBalance: Int,
        completedEvent: SessionEvent
    ): Boolean

    /**
     * Executes the session break/penalty routine atomically.
     * Deducts held funds permanently, updates session status to BROKEN, and logs the transaction and event.
     */
    suspend fun breakSessionTransaction(
        session: Session,
        penaltyTransaction: WalletTransaction,
        newHeldBalance: Int,
        newTotalPenaltiesPaid: Int,
        brokenEvent: SessionEvent
    ): Boolean
}

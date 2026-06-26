package com.lockin.app.core.data.repository

import androidx.room.withTransaction
import com.lockin.app.core.data.local.LockInDatabase
import com.lockin.app.core.data.local.dao.SessionDao
import com.lockin.app.core.data.local.dao.SessionEventDao
import com.lockin.app.core.data.local.dao.WalletDao
import com.lockin.app.core.data.local.dao.WalletTransactionDao
import com.lockin.app.core.data.local.mapper.toDomain
import com.lockin.app.core.data.local.mapper.toEntity
import com.lockin.app.core.domain.model.Session
import com.lockin.app.core.domain.model.SessionEvent
import com.lockin.app.core.domain.model.WalletTransaction
import com.lockin.app.core.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

/**
 * Concrete implementation of the SessionRepository interface.
 * Delegates storage and retrieval operations to Room DAOs and applies object mappers.
 */
class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
    private val sessionEventDao: SessionEventDao,
    private val walletDao: WalletDao,
    private val walletTransactionDao: WalletTransactionDao,
    private val db: LockInDatabase
) : SessionRepository {

    /**
     * Inserts a new session record into the local database.
     */
    override suspend fun insertSession(session: Session) {
        sessionDao.insertSession(session.toEntity())
    }

    /**
     * Updates an existing session record in the local database.
     */
    override suspend fun updateSession(session: Session) {
        sessionDao.updateSession(session.toEntity())
    }

    /**
     * Fetches a session by its unique ID, mapping it to a domain model.
     */
    override suspend fun getSessionById(sessionId: String): Session? {
        return sessionDao.getSessionById(sessionId)?.toDomain()
    }

    /**
     * Streams all sessions ordered by start time, mapped to domain models.
     */
    override fun getAllSessionsFlow(): Flow<List<Session>> {
        return sessionDao.getAllSessionsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Streams the active session, mapping it to a domain model.
     */
    override fun getActiveSessionFlow(): Flow<Session?> {
        return sessionDao.getActiveSessionFlow().map { it?.toDomain() }
    }

    /**
     * Non-reactive check of the active session (one-shot check), mapping it to a domain model.
     */
    override suspend fun getActiveSession(): Session? {
        return sessionDao.getActiveSession()?.toDomain()
    }

    /**
     * Appends a new audit event to the session logs in the local database.
     */
    override suspend fun insertEvent(event: SessionEvent) {
        sessionEventDao.insertEvent(event.toEntity())
    }

    /**
     * Fetches all audit events logged for a specific session, mapped to domain models.
     */
    override suspend fun getEventsForSession(sessionId: String): List<SessionEvent> {
        return sessionEventDao.getEventsForSession(sessionId).map { it.toDomain() }
    }

    /**
     * Executes the session startup routine atomically.
     * Updates available and held balances, inserts the session, and logs the ledger transaction.
     */
    override suspend fun startSessionTransaction(
        session: Session,
        holdTransaction: WalletTransaction,
        newAvailableBalance: Int,
        newHeldBalance: Int
    ): Boolean {
        return try {
            db.withTransaction {
                walletDao.updateAvailableBalance(session.userId, newAvailableBalance, session.startTime)
                walletDao.updateHeldBalance(session.userId, newHeldBalance, session.startTime)
                sessionDao.insertSession(session.toEntity())
                walletTransactionDao.insertTransaction(holdTransaction.toEntity())
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Error starting session in transaction")
            false
        }
    }

    override suspend fun completeSessionTransaction(
        session: Session,
        releaseTransaction: WalletTransaction,
        newAvailableBalance: Int,
        newHeldBalance: Int,
        completedEvent: SessionEvent
    ): Boolean {
        return try {
            db.withTransaction {
                walletDao.updateAvailableBalance(session.userId, newAvailableBalance, completedEvent.timestamp)
                walletDao.updateHeldBalance(session.userId, newHeldBalance, completedEvent.timestamp)
                sessionDao.updateSession(session.toEntity())
                walletTransactionDao.insertTransaction(releaseTransaction.toEntity())
                sessionEventDao.insertEvent(completedEvent.toEntity())
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Error completing session in transaction")
            false
        }
    }

    override suspend fun breakSessionTransaction(
        session: Session,
        penaltyTransaction: WalletTransaction,
        newHeldBalance: Int,
        newTotalPenaltiesPaid: Int,
        brokenEvent: SessionEvent
    ): Boolean {
        return try {
            db.withTransaction {
                val wallet = walletDao.getWallet(session.userId)
                if (wallet != null) {
                    val updatedWallet = wallet.copy(
                        heldBalance = newHeldBalance,
                        totalPenaltiesPaid = newTotalPenaltiesPaid,
                        lastUpdated = brokenEvent.timestamp
                    )
                    walletDao.updateWallet(updatedWallet)
                }
                sessionDao.updateSession(session.toEntity())
                walletTransactionDao.insertTransaction(penaltyTransaction.toEntity())
                sessionEventDao.insertEvent(brokenEvent.toEntity())
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Error breaking session in transaction")
            false
        }
    }
}

/*
 * File: app/src/main/java/com/lockin/app/core/domain/usecase/SyncUseCase.kt
 * Purpose: Use case to synchronize local unsynced sessions, wallet transactions, and events with the server.
 */

package com.lockin.app.core.domain.usecase

import com.lockin.app.core.data.remote.api.SessionApi
import com.lockin.app.core.data.remote.api.WalletApi
import com.lockin.app.core.data.remote.dto.DepositRequest
import com.lockin.app.core.data.remote.dto.HeartbeatRequest
import com.lockin.app.core.data.remote.dto.SessionCreateRequest
import com.lockin.app.core.data.remote.dto.SessionUpdateRequest
import com.lockin.app.core.data.remote.dto.WithdrawRequest
import com.lockin.app.core.domain.model.SessionStatus
import com.lockin.app.core.domain.model.TransactionType
import com.lockin.app.core.domain.repository.SessionRepository
import com.lockin.app.core.domain.repository.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case to scan the local database for unsynced sessions, wallet transactions, and events,
 * and upload them to the remote server.
 */
class SyncUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val walletRepository: WalletRepository,
    private val sessionApi: SessionApi,
    private val walletApi: WalletApi
) {

    /**
     * Scans and uploads all unsynced data to the remote server.
     * Maps local entities to remote DTO schemas and marks them synced upon successful responses.
     */
    suspend operator fun invoke(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Starting background synchronization of unsynced data...")

            // 1. Sync Sessions
            val unsyncedSessions = sessionRepository.getUnsyncedSessions()
            Timber.d("Found ${unsyncedSessions.size} unsynced sessions.")
            for (session in unsyncedSessions) {
                try {
                    // Try creating it first (ignore if it's already there)
                    try {
                        sessionApi.createSession(
                            SessionCreateRequest(
                                sessionId = session.sessionId,
                                penaltyAmount = session.penaltyAmount,
                                startTime = session.startTime,
                                targetEndTime = session.targetEndTime,
                                allowlistVersion = session.allowlistVersion
                            )
                        )
                    } catch (e: Exception) {
                        Timber.d("Session ${session.sessionId} may already exist on server: ${e.message}")
                    }

                    // If it is COMPLETED or BROKEN, resolve it on server
                    if (session.status == SessionStatus.COMPLETED || session.status == SessionStatus.BROKEN) {
                        sessionApi.updateSession(
                            sessionId = session.sessionId,
                            request = SessionUpdateRequest(
                                status = session.status.name,
                                actualEndTime = session.actualEndTime ?: System.currentTimeMillis()
                            )
                        )
                    }

                    // Mark as synced in local DB
                    sessionRepository.markSessionSynced(session.sessionId)

                    // Also mark all transactions tied to this session as synced
                    val sessionTxs = walletRepository.getTransactionsForSession(session.sessionId)
                    for (tx in sessionTxs) {
                        walletRepository.markTransactionSynced(tx.txId)
                    }
                    Timber.d("Successfully synced session ${session.sessionId} and its ledger logs.")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to sync session ${session.sessionId}")
                }
            }

            // 2. Sync Standalone Wallet Transactions (Manual Deposit / Withdrawals)
            val unsyncedTransactions = walletRepository.getUnsyncedTransactions()
            Timber.d("Found ${unsyncedTransactions.size} unsynced standalone transactions.")
            for (tx in unsyncedTransactions) {
                try {
                    when (tx.type) {
                        TransactionType.DEPOSIT -> {
                            walletApi.deposit(
                                DepositRequest(
                                    razorpayPaymentId = tx.txId,
                                    amount = tx.amount
                                )
                            )
                            walletRepository.markTransactionSynced(tx.txId)
                            Timber.d("Successfully synced DEPOSIT transaction ${tx.txId}")
                        }
                        TransactionType.WITHDRAWAL -> {
                            walletApi.withdraw(
                                WithdrawRequest(
                                    amount = tx.amount
                                )
                            )
                            walletRepository.markTransactionSynced(tx.txId)
                            Timber.d("Successfully synced WITHDRAWAL transaction ${tx.txId}")
                        }
                        // Holds, releases, penalties are resolved via session updates, so ignore or mark synced directly
                        TransactionType.SESSION_HOLD, TransactionType.SESSION_RELEASE, TransactionType.PENALTY -> {
                            // If session is synced, these can be marked synced
                            val parentSession = sessionRepository.getSessionById(tx.sessionId ?: "")
                            if (parentSession?.isSynced == true) {
                                walletRepository.markTransactionSynced(tx.txId)
                            }
                        }
                        else -> { /* No-op for AUTO_TOPUP which is pre-synced */ }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to sync transaction ${tx.txId}")
                }
            }

            // 3. Sync Session Events (HEARTBEAT, VPN_GAP, etc.)
            val unsyncedEvents = sessionRepository.getUnsyncedEvents()
            Timber.d("Found ${unsyncedEvents.size} unsynced session events.")
            for (event in unsyncedEvents) {
                try {
                    sessionApi.heartbeat(
                        sessionId = event.sessionId,
                        request = HeartbeatRequest(
                            timestamp = event.timestamp,
                            eventType = event.eventType,
                            metadata = event.metadata
                        )
                    )
                    sessionRepository.markEventSynced(event.eventId)
                    Timber.d("Successfully synced event ${event.eventId} for session ${event.sessionId}")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to sync event ${event.eventId}")
                }
            }

            Timber.d("Data synchronization complete.")
            Result.success(true)
        } catch (e: Exception) {
            Timber.e(e, "Critical error during synchronization execution.")
            Result.failure(e)
        }
    }
}

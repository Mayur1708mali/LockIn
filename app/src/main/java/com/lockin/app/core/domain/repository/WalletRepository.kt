package com.lockin.app.core.domain.repository

import com.lockin.app.core.domain.model.TransactionType
import com.lockin.app.core.domain.model.Wallet
import com.lockin.app.core.domain.model.WalletTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository interface for managing wallet balances, configs, and transactions.
 */
interface WalletRepository {

    /**
     * Streams the user's wallet info to show live updates.
     */
    fun getWalletFlow(userId: String): Flow<Wallet?>

    /**
     * Fetches the user's wallet info (one-shot).
     */
    suspend fun getWallet(userId: String): Wallet?

    /**
     * Inserts the default wallet or replaces the current settings configurations.
     */
    suspend fun insertOrUpdateWallet(wallet: Wallet)

    /**
     * Updates an existing wallet configuration.
     */
    suspend fun updateWallet(wallet: Wallet)

    /**
     * Directly updates the available balance of the user's wallet.
     */
    suspend fun updateAvailableBalance(userId: String, availableBalance: Int, lastUpdated: Long)

    /**
     * Directly updates the held/locked balance of the user's wallet.
     */
    suspend fun updateHeldBalance(userId: String, heldBalance: Int, lastUpdated: Long)

    /**
     * Appends a new credit or debit transaction.
     */
    suspend fun insertTransaction(transaction: WalletTransaction)

    /**
     * Streams the transaction history sorted by date descending.
     */
    fun getAllTransactionsFlow(): Flow<List<WalletTransaction>>

    /**
     * Fetches transactions associated with a specific focus session.
     */
    suspend fun getTransactionsForSession(sessionId: String): List<WalletTransaction>

    /**
     * Executes a deposit transaction atomically.
     * Updates available balance, total deposited, and inserts the transaction log.
     */
    suspend fun depositTransaction(
        userId: String,
        amountPaise: Int,
        transaction: WalletTransaction
    ): Boolean

    /**
     * Executes a withdrawal transaction atomically.
     * Deducts available balance and inserts the transaction log.
     */
    suspend fun withdrawTransaction(
        userId: String,
        amountPaise: Int,
        transaction: WalletTransaction
    ): Boolean

    /**
     * Counts transactions of a specific type since a given timestamp.
     */
    suspend fun getTransactionCountByTypeSince(type: TransactionType, sinceTimestamp: Long): Int
}

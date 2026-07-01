package com.lockin.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lockin.app.core.data.local.entity.WalletTransactionEntity
import com.lockin.app.core.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for wallet transactions.
 */
@Dao
interface WalletTransactionDao {

    /**
     * Appends a new credit or debit transaction ledger record.
     * Enforces strict uniqueness of transaction IDs (aborts on collision).
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: WalletTransactionEntity): Long

    /**
     * Inserts a list of transactions, replacing existing ones on conflict (used for syncing).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<WalletTransactionEntity>): List<Long>

    /**
     * Streams all wallet transactions sorted by date/timestamp descending.
     */
    @Query("SELECT * FROM wallet_transactions ORDER BY timestamp DESC")
    fun getAllTransactionsFlow(): Flow<List<WalletTransactionEntity>>

    /**
     * Fetches transaction entries associated with a specific focus session.
     */
    @Query("SELECT * FROM wallet_transactions WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    suspend fun getTransactionsForSession(sessionId: String): List<WalletTransactionEntity>

    /**
     * Counts transactions of a specific type since a given timestamp.
     */
    @Query("SELECT COUNT(*) FROM wallet_transactions WHERE type = :type AND timestamp >= :sinceTimestamp")
    suspend fun getTransactionCountByTypeSince(type: TransactionType, sinceTimestamp: Long): Int

    /**
     * Retrieves all transactions that are currently not synchronized with the backend.
     */
    @Query("SELECT * FROM wallet_transactions WHERE isSynced = 0")
    suspend fun getUnsyncedTransactions(): List<WalletTransactionEntity>

    /**
     * Marks a wallet transaction as synchronized with the backend.
     */
    @Query("UPDATE wallet_transactions SET isSynced = 1 WHERE txId = :txId")
    suspend fun markTransactionSynced(txId: String): Int
}

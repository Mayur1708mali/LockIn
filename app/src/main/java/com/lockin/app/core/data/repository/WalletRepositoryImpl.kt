package com.lockin.app.core.data.repository

import androidx.room.withTransaction
import com.lockin.app.core.data.local.LockInDatabase
import com.lockin.app.core.data.local.dao.WalletDao
import com.lockin.app.core.data.local.dao.WalletTransactionDao
import com.lockin.app.core.data.local.mapper.toDomain
import com.lockin.app.core.data.local.mapper.toEntity
import com.lockin.app.core.domain.model.TransactionType
import com.lockin.app.core.domain.model.Wallet
import com.lockin.app.core.domain.model.WalletTransaction
import com.lockin.app.core.domain.repository.WalletRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

/**
 * Concrete implementation of the WalletRepository interface.
 * Coordinates wallet transactions and balance audits using local Room DAOs.
 */
class WalletRepositoryImpl @Inject constructor(
    private val walletDao: WalletDao,
    private val walletTransactionDao: WalletTransactionDao,
    private val db: LockInDatabase
) : WalletRepository {

    /**
     * Streams the wallet data for a specific user to reflect live balance updates.
     */
    override fun getWalletFlow(userId: String): Flow<Wallet?> {
        return walletDao.getWalletFlow(userId).map { it?.toDomain() }
    }

    /**
     * Fetches the user's wallet info (one-shot).
     */
    override suspend fun getWallet(userId: String): Wallet? {
        return walletDao.getWallet(userId)?.toDomain()
    }

    /**
     * Inserts the default wallet or replaces the current settings configurations.
     */
    override suspend fun insertOrUpdateWallet(wallet: Wallet) {
        walletDao.insertOrUpdateWallet(wallet.toEntity())
    }

    /**
     * Updates an existing wallet configuration.
     */
    override suspend fun updateWallet(wallet: Wallet) {
        walletDao.updateWallet(wallet.toEntity())
    }

    /**
     * Directly updates the available balance of the user's wallet.
     */
    override suspend fun updateAvailableBalance(userId: String, availableBalance: Int, lastUpdated: Long) {
        walletDao.updateAvailableBalance(userId, availableBalance, lastUpdated)
    }

    /**
     * Directly updates the held/locked balance of the user's wallet.
     */
    override suspend fun updateHeldBalance(userId: String, heldBalance: Int, lastUpdated: Long) {
        walletDao.updateHeldBalance(userId, heldBalance, lastUpdated)
    }

    /**
     * Appends a new credit or debit transaction to the local database.
     */
    override suspend fun insertTransaction(transaction: WalletTransaction) {
        walletTransactionDao.insertTransaction(transaction.toEntity())
    }

    /**
     * Inserts multiple transactions to the local database.
     */
    override suspend fun insertTransactions(transactions: List<WalletTransaction>) {
        walletTransactionDao.insertTransactions(transactions.map { it.toEntity() })
    }

    /**
     * Streams all wallet transactions sorted by date/timestamp descending, mapped to domain models.
     */
    override fun getAllTransactionsFlow(): Flow<List<WalletTransaction>> {
        return walletTransactionDao.getAllTransactionsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Fetches transaction entries associated with a specific focus session, mapped to domain models.
     */
    override suspend fun getTransactionsForSession(sessionId: String): List<WalletTransaction> {
        return walletTransactionDao.getTransactionsForSession(sessionId).map { it.toDomain() }
    }

    /**
     * Executes a deposit transaction atomically.
     * Updates available balance, total deposited, and inserts the transaction log.
     */
    override suspend fun depositTransaction(
        userId: String,
        amountPaise: Int,
        transaction: WalletTransaction
    ): Boolean {
        return try {
            db.withTransaction {
                val wallet = walletDao.getWallet(userId)
                if (wallet != null) {
                    val updatedWallet = wallet.copy(
                        availableBalance = wallet.availableBalance + amountPaise,
                        totalDeposited = wallet.totalDeposited + amountPaise,
                        lastUpdated = transaction.timestamp
                    )
                    walletDao.updateWallet(updatedWallet)
                } else {
                    // Create default wallet if it doesn't exist
                    val newWallet = com.lockin.app.core.data.local.entity.WalletEntity(
                        userId = userId,
                        availableBalance = amountPaise,
                        heldBalance = 0,
                        totalDeposited = amountPaise,
                        totalPenaltiesPaid = 0,
                        autoTopUpEnabled = true, // ON by default
                        autoTopUpThresholdPaise = 20000, // ₹200 threshold default
                        autoTopUpAmountPaise = 50000,   // ₹500 top-up default
                        lastUpdated = transaction.timestamp
                    )
                    walletDao.insertOrUpdateWallet(newWallet)
                }
                walletTransactionDao.insertTransaction(transaction.toEntity())
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Error executing deposit transaction")
            false
        }
    }

    /**
     * Executes a withdrawal transaction atomically.
     * Deducts available balance and inserts the transaction log.
     */
    override suspend fun withdrawTransaction(
        userId: String,
        amountPaise: Int,
        transaction: WalletTransaction
    ): Boolean {
        return try {
            db.withTransaction {
                val wallet = walletDao.getWallet(userId)
                if (wallet != null && wallet.availableBalance >= amountPaise) {
                    val updatedWallet = wallet.copy(
                        availableBalance = wallet.availableBalance - amountPaise,
                        lastUpdated = transaction.timestamp
                    )
                    walletDao.updateWallet(updatedWallet)
                    walletTransactionDao.insertTransaction(transaction.toEntity())
                    true
                } else {
                    Timber.w("Withdrawal failed: wallet is null or insufficient available balance (need %d, have %d)", amountPaise, wallet?.availableBalance ?: 0)
                    false
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error executing withdrawal transaction")
            false
        }
    }

    /**
     * Counts transactions of a specific type since a given timestamp.
     */
    override suspend fun getTransactionCountByTypeSince(type: TransactionType, sinceTimestamp: Long): Int {
        return walletTransactionDao.getTransactionCountByTypeSince(type, sinceTimestamp)
    }

    override suspend fun getUnsyncedTransactions(): List<WalletTransaction> {
        return walletTransactionDao.getUnsyncedTransactions().map { it.toDomain() }
    }

    override suspend fun markTransactionSynced(txId: String) {
        walletTransactionDao.markTransactionSynced(txId)
    }
}

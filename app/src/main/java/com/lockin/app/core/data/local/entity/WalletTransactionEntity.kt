package com.lockin.app.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lockin.app.core.domain.model.TransactionType

/**
 * Room database entity representing a wallet transaction ledger entry (credits/debits).
 */
@Entity(tableName = "wallet_transactions")
data class WalletTransactionEntity(
    @PrimaryKey
    val txId: String,
    val userId: String,
    val type: TransactionType,
    val amount: Int, // in paise
    val direction: String, // "CREDIT" or "DEBIT"
    val sessionId: String?,
    val description: String,
    val timestamp: Long,
    val isSynced: Boolean = false
)

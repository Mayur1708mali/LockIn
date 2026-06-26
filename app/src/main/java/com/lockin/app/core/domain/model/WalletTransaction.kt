package com.lockin.app.core.domain.model

/**
 * Pure Kotlin domain model representing a wallet transaction ledger entry.
 */
data class WalletTransaction(
    val txId: String,
    val userId: String,
    val type: TransactionType,
    val amount: Int, // in paise
    val direction: String, // "CREDIT" or "DEBIT"
    val sessionId: String?,
    val description: String,
    val timestamp: Long
)

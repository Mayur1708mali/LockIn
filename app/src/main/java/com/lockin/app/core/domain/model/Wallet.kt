package com.lockin.app.core.domain.model

/**
 * Pure Kotlin domain model representing the user's detox wallet balance and top-up configurations.
 */
data class Wallet(
    val userId: String,
    val availableBalance: Int, // in paise
    val heldBalance: Int,      // in paise
    val totalDeposited: Int,   // in paise
    val totalPenaltiesPaid: Int, // in paise
    val autoTopUpEnabled: Boolean,
    val autoTopUpThresholdPaise: Int,
    val autoTopUpAmountPaise: Int,
    val lastUpdated: Long
)

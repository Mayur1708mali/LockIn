package com.lockin.app.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room database entity representing the user's detox wallet configuration and balances.
 */
@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey
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

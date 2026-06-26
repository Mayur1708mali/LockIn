package com.lockin.app.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lockin.app.core.domain.model.SessionStatus

/**
 * Room database entity representing a focus session.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val sessionId: String,
    val userId: String,
    val status: SessionStatus,
    val startTime: Long,
    val targetEndTime: Long,
    val actualEndTime: Long?,
    val penaltyAmount: Int, // in paise
    val currency: String = "INR",
    val walletTxHoldId: String?,
    val allowlistVersion: Int,
    val platform: String = "android"
)

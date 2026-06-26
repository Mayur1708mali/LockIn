package com.lockin.app.core.domain.model

/**
 * Pure Kotlin domain model representing a focus detox session.
 */
data class Session(
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

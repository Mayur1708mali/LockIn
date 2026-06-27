/*
 * File: app/src/main/java/com/lockin/app/core/data/remote/dto/NetworkDtos.kt
 * Purpose: Defines data transfer objects (DTOs) for communicating with the remote server.
 */

package com.lockin.app.core.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Request payload to register a device.
 * Why: Used during initial startup to obtain a secure user ID and JWT auth token.
 */
data class RegisterRequest(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("platform") val platform: String = "android"
)

/**
 * Response received after registering a device.
 * Why: Contains the unique user identifier and JWT token used to authenticate subsequent calls.
 */
data class RegisterResponse(
    @SerializedName("userId") val userId: String,
    @SerializedName("token") val token: String
)

/**
 * Request payload to save/update the FCM push token.
 * Why: Informs the backend where to send Firebase Cloud Messaging push alerts.
 */
data class FcmTokenRequest(
    @SerializedName("fcmToken") val fcmToken: String
)

/**
 * Request payload to create a new detox focus session.
 * Why: Signals the server that a session has started and penalty funds should be held.
 */
data class SessionCreateRequest(
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("penaltyAmount") val penaltyAmount: Int, // in paise
    @SerializedName("startTime") val startTime: Long,
    @SerializedName("targetEndTime") val targetEndTime: Long,
    @SerializedName("allowlistVersion") val allowlistVersion: Int
)

/**
 * Request payload to update an active session (e.g., complete or break early).
 * Why: Informs the server of session outcome to settle wallet hold.
 */
data class SessionUpdateRequest(
    @SerializedName("status") val status: String, // "COMPLETED" or "BROKEN"
    @SerializedName("actualEndTime") val actualEndTime: Long
)

/**
 * Request payload to send heartbeats and audit logs during a session.
 * Why: Ensures the server knows the device is active and can detect VPN bypass attempts.
 */
data class HeartbeatRequest(
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("eventType") val eventType: String, // HEARTBEAT, VPN_GAP, etc.
    @SerializedName("metadata") val metadata: String? = null
)

/**
 * Request payload to credit the wallet via a manual Razorpay deposit.
 * Why: Supplies the payment token to verify the deposit transaction server-side.
 */
data class DepositRequest(
    @SerializedName("razorpayPaymentId") val razorpayPaymentId: String,
    @SerializedName("amount") val amount: Int // in paise
)

/**
 * Request payload to initiate a bank withdrawal.
 * Why: Sends request to withdraw available funds back to the user's bank.
 */
data class WithdrawRequest(
    @SerializedName("amount") val amount: Int // in paise
)

/**
 * Request payload to charge the saved Razorpay payment method.
 * Why: Silently deposits money to the user's wallet when below threshold.
 */
data class AutoTopUpRequest(
    @SerializedName("amount") val amount: Int // in paise
)

/**
 * Remote representation of a detox session.
 * Why: Decouples server schema from core database entity or domain models.
 */
data class SessionDto(
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("userId") val userId: String,
    @SerializedName("status") val status: String,
    @SerializedName("startTime") val startTime: Long,
    @SerializedName("targetEndTime") val targetEndTime: Long,
    @SerializedName("actualEndTime") val actualEndTime: Long?,
    @SerializedName("penaltyAmount") val penaltyAmount: Int,
    @SerializedName("currency") val currency: String,
    @SerializedName("walletTxHoldId") val walletTxHoldId: String?,
    @SerializedName("allowlistVersion") val allowlistVersion: Int,
    @SerializedName("platform") val platform: String
)

/**
 * Remote representation of the user's wallet.
 * Why: Holds current balances, configurations, and auto top-up details.
 */
data class WalletDto(
    @SerializedName("userId") val userId: String,
    @SerializedName("availableBalance") val availableBalance: Int,
    @SerializedName("heldBalance") val heldBalance: Int,
    @SerializedName("totalDeposited") val totalDeposited: Int,
    @SerializedName("totalPenaltiesPaid") val totalPenaltiesPaid: Int,
    @SerializedName("autoTopUpEnabled") val autoTopUpEnabled: Boolean,
    @SerializedName("autoTopUpThresholdPaise") val autoTopUpThresholdPaise: Int,
    @SerializedName("autoTopUpAmountPaise") val autoTopUpAmountPaise: Int,
    @SerializedName("lastUpdated") val lastUpdated: Long
)

/**
 * Generic simple response confirming status or messaging from the server.
 * Why: Keeps simple command outcomes clean without custom classes.
 */
data class StatusResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null
)

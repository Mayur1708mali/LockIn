package com.lockin.app.core.domain.model

/**
 * Pure Kotlin domain model representing a logged session audit trail event.
 */
data class SessionEvent(
    val eventId: String,
    val sessionId: String,
    val eventType: String, // HEARTBEAT, VPN_GAP, BREAK_ATTEMPT, BREAK_CONFIRMED, COMPLETED
    val timestamp: Long,
    val metadata: String?
)

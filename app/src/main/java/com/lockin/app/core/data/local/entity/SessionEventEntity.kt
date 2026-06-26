package com.lockin.app.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room database entity representing a logged session event (heartbeat, gap detection, etc.).
 */
@Entity(tableName = "session_events")
data class SessionEventEntity(
    @PrimaryKey
    val eventId: String, // String UUID for consistency
    val sessionId: String,
    val eventType: String, // HEARTBEAT, VPN_GAP, BREAK_ATTEMPT, BREAK_CONFIRMED, COMPLETED
    val timestamp: Long,
    val metadata: String?
)

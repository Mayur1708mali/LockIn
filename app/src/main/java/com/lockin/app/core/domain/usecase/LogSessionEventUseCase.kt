package com.lockin.app.core.domain.usecase

import com.lockin.app.core.domain.model.SessionEvent
import com.lockin.app.core.domain.repository.SessionRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Use case to append a new session audit event to the repository log.
 * Used for logging event trails such as HEARTBEAT, VPN_GAP, BREAK_ATTEMPT, etc.
 */
class LogSessionEventUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {

    /**
     * Appends a new audit event to the session logs.
     *
     * @param sessionId The active focus session ID.
     * @param eventType The type of the event (e.g., "HEARTBEAT", "VPN_GAP", "BREAK_ATTEMPT", "BREAK_CONFIRMED", "COMPLETED").
     * @param metadata Optional metadata describing additional context for the event.
     */
    suspend operator fun invoke(
        sessionId: String,
        eventType: String,
        metadata: String? = null
    ) {
        val event = SessionEvent(
            eventId = UUID.randomUUID().toString(),
            sessionId = sessionId,
            eventType = eventType,
            timestamp = System.currentTimeMillis(),
            metadata = metadata
        )
        sessionRepository.insertEvent(event)
    }
}

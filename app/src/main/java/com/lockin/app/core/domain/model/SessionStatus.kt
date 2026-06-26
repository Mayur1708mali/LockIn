package com.lockin.app.core.domain.model

/**
 * Represents the execution state of a digital detox focus session.
 */
enum class SessionStatus {
    /**
     * Session is created but not yet started/active.
     */
    PENDING,

    /**
     * Session is currently active and VPN blocking is running.
     */
    ACTIVE,

    /**
     * Session was successfully completed (VPN ran to target time).
     */
    COMPLETED,

    /**
     * Session was terminated early by the user through the break-friction gate.
     */
    BROKEN
}

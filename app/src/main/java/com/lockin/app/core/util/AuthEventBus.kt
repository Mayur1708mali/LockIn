/*
 * File: app/src/main/java/com/lockin/app/core/util/AuthEventBus.kt
 * Purpose: Simple event bus to coordinate auth-level lifecycle events globally.
 */

package com.lockin.app.core.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Singleton event bus for security and authentication events.
 * Why: Allows network interceptors to notify UI modules of session expiration (401) without tight coupling.
 */
object AuthEventBus {
    private val _unauthorizedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val unauthorizedEvent = _unauthorizedEvent.asSharedFlow()

    /**
     * Posts a notification that the current user session has expired or is invalid (401).
     * Why: Interceptors call this upon intercepting a 401 response code.
     */
    fun postUnauthorized() {
        _unauthorizedEvent.tryEmit(Unit)
    }
}

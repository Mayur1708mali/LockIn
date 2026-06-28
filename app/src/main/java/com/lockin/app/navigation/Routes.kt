/*
 * File: app/src/main/java/com/lockin/app/navigation/Routes.kt
 * Purpose: Sealed interface defining the type-safe routes (navigation keys)
 * for the LockIn application using Jetpack Navigation 3 and Kotlin Serialization.
 */

package com.lockin.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Sealed interface representing all available navigation destinations in the LockIn app.
 * Extends NavKey for compatibility with AndroidX Navigation 3.
 */
sealed interface LockInRoute : NavKey {

    /**
     * Route for the Onboarding screen flow.
     */
    @Serializable
    data object Onboarding : LockInRoute

    /**
     * Route for the Home dashboard.
     */
    @Serializable
    data object Home : LockInRoute

    /**
     * Route for the Active Session screen.
     * Requires the active session ID, duration, and penalty amount.
     */
    @Serializable
    data class ActiveSession(
        val sessionId: String,
        val durationSeconds: Long,
        val penaltyAmountPaise: Int
    ) : LockInRoute

    /**
     * Route for the Break-Early Friction Gate.
     * Requires the active session ID and penalty amount to show warning details.
     */
    @Serializable
    data class BreakGate(
        val sessionId: String,
        val penaltyAmountPaise: Int
    ) : LockInRoute

    /**
     * Route for the Session Completion/End screen.
     * Takes the session ID and the ending status ("COMPLETED" or "BROKEN").
     */
    @Serializable
    data class SessionComplete(
        val sessionId: String,
        val status: String
    ) : LockInRoute

    /**
     * Route for the Wallet details screen.
     * Optionally triggers the withdrawal bottom sheet directly when navigated from the completion screen.
     */
    @Serializable
    data class Wallet(
        val openWithdrawalSheet: Boolean = false
    ) : LockInRoute

    /**
     * Route for the Session History screen.
     */
    @Serializable
    data object History : LockInRoute

    /**
     * Route for the Settings configuration screen.
     */
    @Serializable
    data object Settings : LockInRoute

    /**
     * Route for the Account details screen.
     */
    @Serializable
    data object Account : LockInRoute
}

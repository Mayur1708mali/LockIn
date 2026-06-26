/*
 * File: app/src/main/java/com/lockin/app/core/domain/model/AllowedApp.kt
 * Purpose: Domain model representing user custom allowed applications.
 */

package com.lockin.app.core.domain.model

/**
 * Domain model representing a custom allowed application that can bypass the VPN.
 *
 * @param packageName The unique application identifier/package name.
 * @param appName The human-readable name of the application.
 * @param addedAt Timestamp when the application was added.
 */
data class AllowedApp(
    val packageName: String,
    val appName: String,
    val addedAt: Long
)

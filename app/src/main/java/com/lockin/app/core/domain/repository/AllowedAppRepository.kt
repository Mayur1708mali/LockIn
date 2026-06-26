/*
 * File: app/src/main/java/com/lockin/app/core/domain/repository/AllowedAppRepository.kt
 * Purpose: Repository interface for custom allowed applications management.
 */

package com.lockin.app.core.domain.repository

import com.lockin.app.core.domain.model.AllowedApp
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository interface for managing custom whitelisted apps.
 */
interface AllowedAppRepository {
    /**
     * Streams the list of all custom allowed apps.
     */
    fun getAllAllowedAppsFlow(): Flow<List<AllowedApp>>

    /**
     * One-shot retrieval of all custom allowed apps.
     */
    suspend fun getAllAllowedApps(): List<AllowedApp>

    /**
     * Adds an app to the allowlist.
     *
     * @param packageName Package identifier.
     * @param appName User-friendly label.
     */
    suspend fun addAllowedApp(packageName: String, appName: String): Boolean

    /**
     * Removes an app from the allowlist by package name.
     *
     * @param packageName Package identifier.
     */
    suspend fun removeAllowedApp(packageName: String): Boolean
}

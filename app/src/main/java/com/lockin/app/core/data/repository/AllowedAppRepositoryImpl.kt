/*
 * File: app/src/main/java/com/lockin/app/core/data/repository/AllowedAppRepositoryImpl.kt
 * Purpose: Implementation of AllowedAppRepository managing whitelisted app packages.
 * Coordinates between Room allowed_apps table and the domain-level Settings feature.
 */

package com.lockin.app.core.data.repository

import com.lockin.app.core.data.local.dao.AllowedAppDao
import com.lockin.app.core.data.local.entity.AllowedAppEntity
import com.lockin.app.core.data.local.mapper.toDomain
import com.lockin.app.core.domain.model.AllowedApp
import com.lockin.app.core.domain.repository.AllowedAppRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Repository implementation for managing AllowedApp database records.
 */
class AllowedAppRepositoryImpl @Inject constructor(
    private val allowedAppDao: AllowedAppDao
) : AllowedAppRepository {

    /**
     * Streams the custom allowed apps list from the database.
     *
     * @return Flow containing the list of custom AllowedApps.
     */
    override fun getAllAllowedAppsFlow(): Flow<List<AllowedApp>> {
        return allowedAppDao.getAllAllowedAppsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Returns a one-shot snapshot list of all custom allowed apps.
     *
     * @return Snapshotted list of custom AllowedApps.
     */
    override suspend fun getAllAllowedApps(): List<AllowedApp> {
        return allowedAppDao.getAllAllowedApps().map { it.toDomain() }
    }

    /**
     * Persists an application package in the database whitelist.
     *
     * @param packageName Package identifier.
     * @param appName User-friendly label.
     * @return Boolean indicating database transaction outcome.
     */
    override suspend fun addAllowedApp(packageName: String, appName: String): Boolean {
        return try {
            val entity = AllowedAppEntity(
                packageName = packageName,
                appName = appName,
                addedAt = System.currentTimeMillis()
            )
            allowedAppDao.insertAllowedApp(entity)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Deletes an app package entry from the database.
     *
     * @param packageName Package identifier.
     * @return Boolean indicating database deletion outcome.
     */
    override suspend fun removeAllowedApp(packageName: String): Boolean {
        return try {
            allowedAppDao.deleteAllowedApp(packageName)
            true
        } catch (e: Exception) {
            false
        }
    }
}

/*
 * File: com/lockin/app/core/data/local/dao/AllowedAppDao.kt
 * Purpose: DAO interface defining database operations for custom allowed apps.
 */

package com.lockin.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lockin.app.core.data.local.entity.AllowedAppEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for custom allowed applications during sessions.
 */
@Dao
interface AllowedAppDao {

    /**
     * Streams the list of all custom allowed apps sorted by creation timestamp.
     */
    @Query("SELECT * FROM allowed_apps ORDER BY addedAt ASC")
    fun getAllAllowedAppsFlow(): Flow<List<AllowedAppEntity>>

    /**
     * One-shot retrieval of all custom allowed apps.
     */
    @Query("SELECT * FROM allowed_apps ORDER BY addedAt ASC")
    suspend fun getAllAllowedApps(): List<AllowedAppEntity>

    /**
     * Inserts or replaces a custom allowed app.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllowedApp(app: AllowedAppEntity): Long

    /**
     * Removes an app package from the allowlist by package name.
     */
    @Query("DELETE FROM allowed_apps WHERE packageName = :packageName")
    suspend fun deleteAllowedApp(packageName: String): Int
}

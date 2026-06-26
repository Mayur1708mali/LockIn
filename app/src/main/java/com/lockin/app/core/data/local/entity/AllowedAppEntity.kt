/*
 * File: com/lockin/app/core/data/local/entity/AllowedAppEntity.kt
 * Purpose: Room database entity representing custom user allowed packages.
 */

package com.lockin.app.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Database entity for user-custom allowed applications during lock detox sessions.
 * Matches the required packages allowlist configuration.
 *
 * @param packageName The unique application identifier/package name (e.g. "com.example.app").
 * @param appName The human-readable name of the application.
 * @param addedAt Timestamp when the application was added to the allowlist.
 */
@Entity(tableName = "allowed_apps")
data class AllowedAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val addedAt: Long
)

/*
 * File: app/src/main/java/com/lockin/app/core/domain/usecase/SignOutUseCase.kt
 * Purpose: Use case to execute secure user sign-out and local data cleanup.
 */

package com.lockin.app.core.domain.usecase

import com.lockin.app.core.data.local.LockInDatabase
import com.lockin.app.core.security.EncryptedPrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Handles clearing all user credentials, security tokens, and local database entries during sign-out.
 * Why: Restores the application to a clean onboarding state and protects user data.
 */
class SignOutUseCase @Inject constructor(
    private val encryptedPrefsManager: EncryptedPrefsManager,
    private val database: LockInDatabase
) {

    /**
     * Clear auth preferences and database tables.
     * Why: Enforces complete local data reset upon leaving focus mode.
     */
    suspend operator fun invoke() {
        withContext(Dispatchers.IO) {
            // 1. Reset user identifier and auth tokens
            encryptedPrefsManager.clearAuth()

            // 2. Erase local tables (sessions, transactions, wallet configuration)
            database.clearAllTables()
        }
    }
}

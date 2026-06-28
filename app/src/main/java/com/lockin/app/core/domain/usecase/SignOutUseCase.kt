/*
 * File: app/src/main/java/com/lockin/app/core/domain/usecase/SignOutUseCase.kt
 * Purpose: Use case to execute secure user sign-out and local data cleanup.
 */

package com.lockin.app.core.domain.usecase

import com.lockin.app.core.data.local.LockInDatabase
import com.lockin.app.core.security.EncryptedPrefsManager
import com.lockin.app.feature.auth.GoogleSignInManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Handles clearing all user credentials, display details, auto top-up configs, security tokens, and clearing local database tables during sign-out.
 * Why: Restores the application to a clean onboarding state and protects user data by ensuring no traces of data remain on device after signout.
 */
class SignOutUseCase @Inject constructor(
    private val encryptedPrefsManager: EncryptedPrefsManager,
    private val lockInDatabase: LockInDatabase,
    private val googleSignInManager: GoogleSignInManager
) {

    /**
     * Clear auth preferences, display name, email, auto top-up configurations, delete all local database tables, and clear Google Sign-In state.
     * Why: Enforces thorough security cleanup and privacy compliance by purging all user session, transaction, and wallet data, and revokes auto-select on next login.
     */
    suspend operator fun invoke() {
        withContext(Dispatchers.IO) {
            // 1. Reset user identifier, auth tokens, display name, email, and onboarding completion
            encryptedPrefsManager.clearAuth()

            // 2. Clear auto top-up preferences and saved payment instruments
            encryptedPrefsManager.clearAutoTopUpConfig()

            // 3. Delete all local database tables (sessions, events, wallets, transactions, allowlisted apps)
            lockInDatabase.clearAllTables()

            // 4. Clear Google Credential Manager active session to prevent automated silent re-authentication
            googleSignInManager.clearSession()
        }
    }
}

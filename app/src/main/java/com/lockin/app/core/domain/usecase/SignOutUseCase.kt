/*
 * File: app/src/main/java/com/lockin/app/core/domain/usecase/SignOutUseCase.kt
 * Purpose: Use case to execute secure user sign-out and local data cleanup.
 */

package com.lockin.app.core.domain.usecase

import com.lockin.app.core.security.EncryptedPrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Handles clearing all user credentials, display details, auto top-up configs and security tokens during sign-out.
 * Why: Restores the application to a clean onboarding state and protects user data while preserving database history.
 */
class SignOutUseCase @Inject constructor(
    private val encryptedPrefsManager: EncryptedPrefsManager
) {

    /**
     * Clear auth preferences, display name, email, and auto top-up configurations.
     * Why: Enforces security cleanup without destroying local session or transaction histories.
     */
    suspend operator fun invoke() {
        withContext(Dispatchers.IO) {
            // 1. Reset user identifier, auth tokens, display name, email, and onboarding completion
            encryptedPrefsManager.clearAuth()

            // 2. Clear auto top-up preferences and saved payment instruments
            encryptedPrefsManager.clearAutoTopUpConfig()
        }
    }
}

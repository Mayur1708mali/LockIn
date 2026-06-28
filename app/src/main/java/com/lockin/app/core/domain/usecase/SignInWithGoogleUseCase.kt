/*
 * File: app/src/main/java/com/lockin/app/core/domain/usecase/SignInWithGoogleUseCase.kt
 * Purpose: Use case to execute Google Authentication backend verification.
 */

package com.lockin.app.core.domain.usecase

import com.lockin.app.core.data.remote.api.AuthApi
import com.lockin.app.core.data.remote.dto.GoogleSignInRequest
import com.lockin.app.core.data.remote.dto.GoogleSignInResponse
import com.lockin.app.core.security.EncryptedPrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Coordinates verifying Google ID tokens with the backend and storing JWT tokens / profiles locally.
 * Why: Encapsulates user login/registration operations under a clean use case definition.
 */
class SignInWithGoogleUseCase @Inject constructor(
    private val authApi: AuthApi,
    private val encryptedPrefsManager: EncryptedPrefsManager
) {

    /**
     * Sends the Google ID token to the server and saves the returned JWT.
     * Why: Authenticates the user and sets up locally encrypted profile credentials.
     *
     * @param idToken Google ID token fetched from Credential Manager.
     * @return Result wrapping the server's GoogleSignInResponse.
     */
    suspend operator fun invoke(idToken: String): Result<GoogleSignInResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = GoogleSignInRequest(idToken)
                val response = authApi.signInWithGoogle(request)

                // Save JWT under key "auth_jwt"
                encryptedPrefsManager.saveAuthJwt(response.jwt)

                // Save user profile details
                encryptedPrefsManager.saveUserId(response.userId)
                encryptedPrefsManager.saveGoogleDisplayName(response.displayName)
                encryptedPrefsManager.saveGoogleEmail(response.email)

                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

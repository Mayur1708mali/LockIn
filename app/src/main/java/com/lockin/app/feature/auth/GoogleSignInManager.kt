/*
 * File: app/src/main/java/com/lockin/app/feature/auth/GoogleSignInManager.kt
 * Purpose: Manager class for Credential Manager API calls to initiate Google Sign-In.
 */

package com.lockin.app.feature.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.lockin.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles modern Google Sign-In operations using Android X Credential Manager.
 * Why: Unified replacement for the deprecated legacy GoogleSignInClient.
 */
@Singleton
class GoogleSignInManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Instantiate CredentialManager using application context
    private val credentialManager = CredentialManager.create(context)

    /**
     * Triggers Google Sign-In bottom sheet/dialog to fetch the user's ID token.
     * Why: Authenticates the user with their Google Account on the local device.
     *
     * @param activityContext Must be an Activity context required to show the dialog interface.
     * @return Result wrapping the Google ID token string on success, or exception on failure.
     */
    suspend fun signIn(activityContext: Context): Result<String> {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(com.lockin.app.BuildConfig.GOOGLE_CLIENT_ID)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            Timber.d("GoogleSignInManager: Launching Credential Manager dialog...")
            val response = credentialManager.getCredential(
                context = activityContext,
                request = request
            )

            handleCredential(response)
        } catch (e: Exception) {
            Timber.e(e, "GoogleSignInManager: Google Sign-In failed.")
            Result.failure(e)
        }
    }

    /**
     * Parses the CredentialManager response and extracts the Google ID Token.
     * Why: Translates generic Credential format to concrete GoogleIdTokenCredential.
     */
    private fun handleCredential(result: GetCredentialResponse): Result<String> {
        val credential = result.credential
        return try {
            if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                Timber.d("GoogleSignInManager: Successfully extracted Google ID token via type matching.")
                Result.success(googleIdTokenCredential.idToken)
            } else if (credential is GoogleIdTokenCredential) {
                Timber.d("GoogleSignInManager: Successfully extracted Google ID token via class matching.")
                Result.success(credential.idToken)
            } else {
                val errorMsg = "Unsupported credential returned: ${credential.type}"
                Timber.e("GoogleSignInManager: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Timber.e(e, "GoogleSignInManager: Exception while parsing Google ID token credential.")
            Result.failure(e)
        }
    }
}

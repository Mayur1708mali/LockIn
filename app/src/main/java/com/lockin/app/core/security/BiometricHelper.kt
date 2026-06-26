/*
 * File: com/lockin/app/core/security/BiometricHelper.kt
 * Purpose: Helper class that wraps Android's BiometricPrompt API, providing
 * a clean, coroutine-based Flow interface for biometric authentication.
 * Used for high-security actions like session break gates and manual wallet withdrawals.
 */

package com.lockin.app.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result states for biometric authentication flow.
 */
sealed interface BiometricResult {
    /**
     * Authentication was successful.
     */
    object Success : BiometricResult

    /**
     * An individual authentication attempt failed (e.g., unrecognized fingerprint),
     * but the prompt remains active for further attempts.
     */
    object Failure : BiometricResult

    /**
     * A terminal error occurred (e.g., user cancelled, hardware unavailable, lockout),
     * causing the prompt to close.
     */
    data class Error(val errorCode: Int, val errorMessage: String) : BiometricResult
}

/**
 * Helper class to manage biometric authentication requests.
 * Wraps BiometricPrompt to return a reactive Flow of BiometricResult.
 */
@Singleton
class BiometricHelper @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /**
     * Initiates biometric authentication.
     * Displays a system biometric prompt configured with Strong biometric requirements
     * and no device credential/PIN fallback as required by LockIn security rules.
     *
     * @param activity The FragmentActivity hosting the prompt.
     * @param title The title for the prompt dialog.
     * @param subtitle The subtitle for the prompt dialog.
     * @param negativeButtonText Text for the negative/cancel button.
     * @return A Flow emitting BiometricResult states as the user interacts with the prompt.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String = "Cancel"
    ): Flow<BiometricResult> = callbackFlow {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Timber.e("Biometric authentication terminal error: $errorCode - $errString")
                trySend(BiometricResult.Error(errorCode, errString.toString()))
                close()
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Timber.d("Biometric authentication succeeded")
                trySend(BiometricResult.Success)
                close()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Timber.w("Biometric authentication attempt failed (unrecognized biometric)")
                trySend(BiometricResult.Failure)
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        // LockIn requires BIOMETRIC_STRONG (no PIN/Pattern fallback allowed)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText(negativeButtonText)
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start biometric prompt")
            trySend(BiometricResult.Error(-1, e.message ?: "Unknown error starting biometric prompt"))
            close()
        }

        // Cancel authentication if the coroutine flow collection is cancelled/closed
        awaitClose {
            biometricPrompt.cancelAuthentication()
        }
    }

    /**
     * Checks if strong biometric authentication is supported and set up on the device.
     *
     * @return True if strong biometrics can be authenticated, false otherwise.
     */
    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        val result = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        Timber.d("Biometric availability check result: $result")
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Retrieves the raw status code for biometric availability.
     * Useful for checking specific error conditions (e.g., BIOMETRIC_ERROR_NONE_ENROLLED).
     *
     * @return The integer status code from BiometricManager.canAuthenticate.
     */
    fun getBiometricStatus(): Int {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    }
}

package com.lockin.app.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.lockin.app.core.domain.model.AutoTopUpConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager class for handling secure local storage operations using EncryptedSharedPreferences.
 * Stores sensitive credentials, configs, user identifiers, and payment tokens.
 */
@Singleton
class EncryptedPrefsManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val sharedPreferences: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "lockin_secure_preferences",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.e(e, "Error initializing EncryptedSharedPreferences, falling back to standard SharedPreferences")
            // In case keystore/encryption is corrupted/fails, fallback to prevent crash in debug/dev
            context.getSharedPreferences("lockin_fallback_preferences", Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val KEY_RAZORPAY_TOKEN = "key_razorpay_token"
        private const val KEY_AUTH_TOKEN = "key_auth_token"
        private const val KEY_AUTH_JWT = "auth_jwt"
        private const val KEY_USER_ID = "key_user_id"
        private const val KEY_AUTO_TOPUP_ENABLED = "key_auto_topup_enabled"
        private const val KEY_AUTO_TOPUP_THRESHOLD = "key_auto_topup_threshold"
        private const val KEY_AUTO_TOPUP_AMOUNT = "key_auto_topup_amount"
        private const val KEY_PAYMENT_METHOD_LABEL = "key_payment_method_label"
        private const val KEY_IS_ROOTED = "key_is_rooted"
        private const val KEY_ONBOARDING_COMPLETE = "key_onboarding_complete"
        private const val KEY_FCM_TOKEN = "key_fcm_token"
        private const val KEY_GOOGLE_DISPLAY_NAME = "google_display_name"
        private const val KEY_GOOGLE_EMAIL = "google_email"
    }

    /**
     * Saves the FCM registration token securely.
     */
    fun saveFcmToken(token: String) {
        sharedPreferences.edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    /**
     * Retrieves the saved FCM registration token.
     */
    fun getFcmToken(): String? {
        return sharedPreferences.getString(KEY_FCM_TOKEN, null)
    }

    /**
     * Saves the device root detection status.
     */
    fun saveRootStatus(isRooted: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_IS_ROOTED, isRooted).apply()
    }

    /**
     * Retrieves the saved device root status.
     */
    fun isDeviceRooted(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_ROOTED, false)
    }

    /**
     * Saves whether the user has completed onboarding.
     */
    fun saveOnboardingComplete(complete: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).apply()
    }

    /**
     * Retrieves whether the user has completed onboarding.
     */
    fun isOnboardingComplete(): Boolean {
        return sharedPreferences.getBoolean(KEY_ONBOARDING_COMPLETE, false)
    }

    /**
     * Saves the Razorpay payment method token securely.
     */
    fun saveToken(token: String) {
        sharedPreferences.edit().putString(KEY_RAZORPAY_TOKEN, token).apply()
    }

    /**
     * Retrieves the saved Razorpay payment method token.
     */
    fun getToken(): String? {
        return sharedPreferences.getString(KEY_RAZORPAY_TOKEN, null)
    }

    /**
     * Saves the authenticated user JWT token securely.
     */
    fun saveAuthToken(token: String) {
        sharedPreferences.edit().putString(KEY_AUTH_TOKEN, token).apply()
    }

    /**
     * Retrieves the authenticated user JWT token.
     */
    fun getAuthToken(): String? {
        return sharedPreferences.getString(KEY_AUTH_TOKEN, null)
    }

    /**
     * Saves the authenticated user JWT token securely under key "auth_jwt".
     * Why: Required specifically under key "auth_jwt" by the Google Sign-in requirements.
     */
    fun saveAuthJwt(token: String?) {
        sharedPreferences.edit().putString(KEY_AUTH_JWT, token).apply()
    }

    /**
     * Retrieves the authenticated user JWT token from key "auth_jwt".
     * Why: Required specifically under key "auth_jwt" by the Google Sign-in requirements.
     */
    fun getAuthJwt(): String? {
        return sharedPreferences.getString(KEY_AUTH_JWT, null)
    }

    /**
     * Saves the user's Google display name securely.
     */
    fun saveGoogleDisplayName(name: String?) {
        sharedPreferences.edit().putString(KEY_GOOGLE_DISPLAY_NAME, name).apply()
    }

    /**
     * Retrieves the user's Google display name.
     */
    fun getGoogleDisplayName(): String? {
        return sharedPreferences.getString(KEY_GOOGLE_DISPLAY_NAME, null)
    }

    /**
     * Saves the user's Google email securely.
     */
    fun saveGoogleEmail(email: String?) {
        sharedPreferences.edit().putString(KEY_GOOGLE_EMAIL, email).apply()
    }

    /**
     * Retrieves the user's Google email.
     */
    fun getGoogleEmail(): String? {
        return sharedPreferences.getString(KEY_GOOGLE_EMAIL, null)
    }

    /**
     * Clears all authentication-related preferences.
     * Why: Clears credentials on sign out.
     */
    fun clearAuth() {
        sharedPreferences.edit()
            .remove(KEY_AUTH_JWT)
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_GOOGLE_DISPLAY_NAME)
            .remove(KEY_GOOGLE_EMAIL)
            .remove(KEY_ONBOARDING_COMPLETE)
            .apply()
    }

    /**
     * Saves the logged-in user ID securely.
     */
    fun saveUserId(userId: String) {
        sharedPreferences.edit().putString(KEY_USER_ID, userId).apply()
    }

    /**
     * Retrieves the saved user ID.
     */
    fun getUserId(): String? {
        return sharedPreferences.getString(KEY_USER_ID, null)
    }

    /**
     * Saves the auto top-up config settings.
     */
    fun saveAutoTopUpConfig(config: AutoTopUpConfig) {
        sharedPreferences.edit().apply {
            putBoolean(KEY_AUTO_TOPUP_ENABLED, config.autoTopUpEnabled)
            putInt(KEY_AUTO_TOPUP_THRESHOLD, config.autoTopUpThresholdPaise)
            putInt(KEY_AUTO_TOPUP_AMOUNT, config.autoTopUpAmountPaise)
            putString(KEY_PAYMENT_METHOD_LABEL, config.savedPaymentMethodLabel)
        }.apply()
    }

    /**
     * Retrieves the auto top-up config settings.
     */
    fun getAutoTopUpConfig(): AutoTopUpConfig {
        return AutoTopUpConfig(
            autoTopUpEnabled = sharedPreferences.getBoolean(KEY_AUTO_TOPUP_ENABLED, true),
            autoTopUpThresholdPaise = sharedPreferences.getInt(KEY_AUTO_TOPUP_THRESHOLD, 20000), // Default ₹200
            autoTopUpAmountPaise = sharedPreferences.getInt(KEY_AUTO_TOPUP_AMOUNT, 50000),     // Default ₹500
            savedPaymentMethodLabel = sharedPreferences.getString(KEY_PAYMENT_METHOD_LABEL, null)
        )
    }
}

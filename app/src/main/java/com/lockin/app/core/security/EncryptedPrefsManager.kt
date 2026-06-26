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
        private const val KEY_USER_ID = "key_user_id"
        private const val KEY_AUTO_TOPUP_ENABLED = "key_auto_topup_enabled"
        private const val KEY_AUTO_TOPUP_THRESHOLD = "key_auto_topup_threshold"
        private const val KEY_AUTO_TOPUP_AMOUNT = "key_auto_topup_amount"
        private const val KEY_PAYMENT_METHOD_LABEL = "key_payment_method_label"
        private const val KEY_IS_ROOTED = "key_is_rooted"
        private const val KEY_ONBOARDING_COMPLETE = "key_onboarding_complete"
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

package com.lockin.app.core.domain.usecase

import com.lockin.app.core.domain.model.AutoTopUpConfig
import com.lockin.app.core.security.EncryptedPrefsManager
import javax.inject.Inject

/**
 * Use case to save/update the automatic top-up configurations and saved payment token securely.
 */
class SaveAutoTopUpConfigUseCase @Inject constructor(
    private val encryptedPrefsManager: EncryptedPrefsManager
) {

    /**
     * Persists the auto top-up config settings and optional Razorpay payment token.
     *
     * @param config The AutoTopUpConfig settings.
     * @param token Optional Razorpay payment/saved-method token.
     */
    operator fun invoke(config: AutoTopUpConfig, token: String? = null) {
        encryptedPrefsManager.saveAutoTopUpConfig(config)
        token?.let { encryptedPrefsManager.saveToken(it) }
    }
}

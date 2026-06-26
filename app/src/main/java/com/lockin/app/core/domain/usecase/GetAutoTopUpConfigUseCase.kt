package com.lockin.app.core.domain.usecase

import com.lockin.app.core.domain.model.AutoTopUpConfig
import com.lockin.app.core.security.EncryptedPrefsManager
import javax.inject.Inject

/**
 * Use case to retrieve the automatic top-up configurations from secure storage.
 */
class GetAutoTopUpConfigUseCase @Inject constructor(
    private val encryptedPrefsManager: EncryptedPrefsManager
) {

    /**
     * Retrieves the saved auto top-up config settings.
     *
     * @return The AutoTopUpConfig settings.
     */
    operator fun invoke(): AutoTopUpConfig {
        return encryptedPrefsManager.getAutoTopUpConfig()
    }
}

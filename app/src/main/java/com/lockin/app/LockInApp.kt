package com.lockin.app

import android.app.Application
import com.lockin.app.core.security.EncryptedPrefsManager
import com.lockin.app.core.security.RootDetectionManager
import com.lockin.app.core.security.RootStatus
import com.lockin.app.core.notification.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

/**
 * Main Application class for LockIn.
 * Initializes dependency injection, logger (Timber), and checks for root access on launch.
 */
@HiltAndroidApp
class LockInApp : Application() {

    @Inject
    lateinit var rootDetectionManager: RootDetectionManager

    @Inject
    lateinit var encryptedPrefsManager: EncryptedPrefsManager

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber logging in debug builds
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("Timber initialized in debug mode.")
        }

        // Initialize Notification Channels
        NotificationChannels.createNotificationChannels(this)

        // Perform root detection on launch and store the result securely
        checkRootStatus()
    }

    /**
     * Checks if the device is rooted using RootDetectionManager and stores the result
     * in EncryptedSharedPreferences to surface warning banners on the home screen.
     */
    private fun checkRootStatus() {
        try {
            val rootStatus = rootDetectionManager.checkRootStatus()
            val isRooted = rootStatus == RootStatus.ROOTED
            encryptedPrefsManager.saveRootStatus(isRooted)
            
            if (isRooted) {
                Timber.w("Root detection: Device is ROOTED. Warning banner will be surfaced on the home screen.")
            } else {
                Timber.i("Root detection: Device is secure.")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking or storing root status.")
        }
    }
}

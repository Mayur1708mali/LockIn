package com.lockin.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.lockin.app.core.security.EncryptedPrefsManager
import com.lockin.app.core.security.RootDetectionManager
import com.lockin.app.core.security.RootStatus
import com.lockin.app.core.notification.NotificationChannels
import com.lockin.app.service.AutoTopUpService
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Main Application class for LockIn.
 * Initializes dependency injection, logger (Timber), registers notification channels,
 * performs root status diagnostics, and schedules background wallet auto top-up checks.
 */
@HiltAndroidApp
class LockInApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var rootDetectionManager: RootDetectionManager

    @Inject
    lateinit var encryptedPrefsManager: EncryptedPrefsManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

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

        // Schedule periodic auto top-up checking
        scheduleAutoTopUp()
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

    /**
     * Schedules the WorkManager periodic task for AutoTopUpService.
     * Checks wallet status every 30 minutes. Uses KEEP policy to prevent resetting
     * the schedule if the app restarts.
     */
    private fun scheduleAutoTopUp() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val autoTopUpRequest = PeriodicWorkRequestBuilder<AutoTopUpService>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "AutoTopUpWork",
                ExistingPeriodicWorkPolicy.KEEP,
                autoTopUpRequest
            )
            Timber.i("Unique periodic work for AutoTopUpService enqueued successfully.")
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule AutoTopUpService periodic work.")
        }
    }
}

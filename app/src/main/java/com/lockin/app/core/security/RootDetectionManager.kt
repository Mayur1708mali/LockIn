/*
 * File: com/lockin/app/core/security/RootDetectionManager.kt
 * Purpose: Wrapper for the RootBeer library to check if the device is rooted.
 * Provides a clean abstraction that returns a RootStatus enum.
 */

package com.lockin.app.core.security

import android.content.Context
import com.scottyab.rootbeer.RootBeer
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents the root status of the device.
 */
enum class RootStatus {
    /**
     * The device appears to be secure and not rooted.
     */
    SAFE,

    /**
     * Root access or custom binary signatures (e.g. su binary) were detected.
     */
    ROOTED
}

/**
 * Manager class responsible for checking the device root status using RootBeer.
 */
@Singleton
class RootDetectionManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val rootBeer = RootBeer(context)

    /**
     * Performs a root detection scan using RootBeer.
     * Evaluates multiple system properties, su binaries, and read/write paths.
     *
     * @return The determined [RootStatus] of the device.
     */
    fun checkRootStatus(): RootStatus {
        Timber.d("Starting root detection check...")
        val isRooted = try {
            rootBeer.isRooted
        } catch (e: Exception) {
            Timber.e(e, "Error occurred during root detection scan")
            false // Default to false if root detection itself crashes
        }

        return if (isRooted) {
            Timber.w("Root access detected on this device!")
            RootStatus.ROOTED
        } else {
            Timber.d("Device is clean. No root access detected.")
            RootStatus.SAFE
        }
    }
}

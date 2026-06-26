/*
 * File: com/lockin/app/service/AllowlistManager.kt
 * Purpose: Manages allowed applications list that bypass the digital detox VPN.
 * Merges system-wide default apps (payment/emergency) with user custom database choices.
 */

package com.lockin.app.service

import android.content.Context
import com.lockin.app.core.data.local.dao.AllowedAppDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the application package allowlist configuration.
 * Apps in this list bypass the local VPN blocks to allow payments and emergency actions.
 */
@Singleton
class AllowlistManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val allowedAppDao: AllowedAppDao
) {

    // Default payment and emergency packages that are always whitelisted
    private val defaultAllowlist = listOf(
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.phonepe.app",                         // PhonePe
        "net.one97.communications",                // Paytm
        "in.org.npci.upiapp",                      // BHIM UPI
        "com.android.emergency"                    // Emergency services
    )

    /**
     * Resolves the complete list of packages that should bypass the VPN.
     * Merges default packages, custom user-allowed packages from Room, and the host application itself.
     *
     * @return List of package name strings.
     */
    suspend fun getFullAllowlist(): List<String> {
        val ownPackage = context.packageName
        val userApps = try {
            allowedAppDao.getAllAllowedApps().map { it.packageName }
        } catch (e: Exception) {
            emptyList()
        }
        // Deduplicate and combine
        return (defaultAllowlist + userApps + ownPackage).distinct()
    }
}

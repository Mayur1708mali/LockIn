/*
 * File: com/lockin/app/core/notification/NotificationChannels.kt
 * Purpose: Defines and registers notification channels for the LockIn application.
 */

package com.lockin.app.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Helper object to define and initialize notification channels used by the app.
 */
object NotificationChannels {
    const val SESSION_CHANNEL_ID = "SESSION"
    const val WALLET_CHANNEL_ID = "WALLET"
    const val SYSTEM_CHANNEL_ID = "SYSTEM"

    /**
     * Creates and registers the notification channels with the system.
     * Must be called during App startup.
     *
     * @param context The application context.
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Session Channel: High importance for active locks and countdowns
            val sessionChannel = NotificationChannel(
                SESSION_CHANNEL_ID,
                "Focus Sessions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Persistent session countdown and active lock alerts"
                setShowBadge(true)
            }

            // Wallet Channel: Default importance for balance and auto top-up alerts
            val walletChannel = NotificationChannel(
                WALLET_CHANNEL_ID,
                "Wallet Transactions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Deposit, withdrawal, and auto top-up status alerts"
                setShowBadge(true)
            }

            // System Channel: Low importance for passive updates and logging
            val systemChannel = NotificationChannel(
                SYSTEM_CHANNEL_ID,
                "System Messages",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "General system logs and minor status updates"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannels(
                listOf(sessionChannel, walletChannel, systemChannel)
            )
        }
    }
}

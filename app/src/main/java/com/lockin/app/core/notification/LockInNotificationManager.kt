/*
 * File: app/src/main/java/com/lockin/app/core/notification/LockInNotificationManager.kt
 * Purpose: Centralized manager for building and posting system notifications.
 */

package com.lockin.app.core.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.lockin.app.MainActivity
import com.lockin.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager class responsible for firing local notifications across the app.
 * Provides specialized functions for each type of notification required by LockIn.
 * Commented every function per Code Generation Rules to explain what it does and why.
 */
@Singleton
class LockInNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    // Get the system-level NotificationManager service
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        // Distinct notification IDs to separate different notification types and avoid overlaps
        private const val SESSION_NOTIFICATION_ID = 10001
        private const val WALLET_NOTIFICATION_ID = 20001
        private const val SYSTEM_NOTIFICATION_ID = 30001
    }

    /**
     * Helper to create a PendingIntent that opens MainActivity.
     * We use this so that clicking any notification brings the user back into the app.
     *
     * @return PendingIntent configuration for launching MainActivity.
     */
    private fun createContentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Shows a notification when a focus session starts.
     * Why: Confirms to the user that the session has begun and they are locked in.
     *
     * @param durationText Readable duration, e.g., "1 hour".
     */
    fun showSessionStartNotification(durationText: String) {
        val title = context.getString(R.string.notification_session_start_title)
        val body = context.getString(R.string.notification_session_start_body, durationText)
        showNotification(
            SESSION_NOTIFICATION_ID,
            NotificationChannels.SESSION_CHANNEL_ID,
            title,
            body,
            NotificationCompat.PRIORITY_HIGH
        )
    }

    /**
     * Shows a notification when a focus session reaches the halfway mark.
     * Why: Gives the user positive reinforcement and status tracking halfway through.
     */
    fun showSessionHalfwayNotification() {
        val title = context.getString(R.string.notification_session_halfway_title)
        val body = context.getString(R.string.notification_session_halfway_body)
        showNotification(
            SESSION_NOTIFICATION_ID,
            NotificationChannels.SESSION_CHANNEL_ID,
            title,
            body,
            NotificationCompat.PRIORITY_HIGH
        )
    }

    /**
     * Shows a warning notification when 15 minutes remain in the session.
     * Why: Alerts the user that their lock is nearing completion so they can prepare.
     */
    fun showSession15MinWarningNotification() {
        val title = context.getString(R.string.notification_session_15min_title)
        val body = context.getString(R.string.notification_session_15min_body)
        showNotification(
            SESSION_NOTIFICATION_ID,
            NotificationChannels.SESSION_CHANNEL_ID,
            title,
            body,
            NotificationCompat.PRIORITY_HIGH
        )
    }

    /**
     * Shows a notification when a focus session is successfully completed.
     * Why: Informs the user of session success and wallet balance release.
     */
    fun showSessionCompleteNotification() {
        val title = context.getString(R.string.notification_session_complete_title)
        val body = context.getString(R.string.notification_session_complete_body)
        showNotification(
            SESSION_NOTIFICATION_ID,
            NotificationChannels.SESSION_CHANNEL_ID,
            title,
            body,
            NotificationCompat.PRIORITY_HIGH
        )
    }

    /**
     * Shows a notification when a VPN gap is detected (VPN connection interrupted).
     * Why: Crucial warning that the detox lock has dropped so the user knows why traffic is blocked/unblocked.
     */
    fun showVpnGapNotification() {
        val title = context.getString(R.string.notification_vpn_gap_title)
        val body = context.getString(R.string.notification_vpn_gap_body)
        showNotification(
            SESSION_NOTIFICATION_ID,
            NotificationChannels.SESSION_CHANNEL_ID,
            title,
            body,
            NotificationCompat.PRIORITY_HIGH
        )
    }

    /**
     * Shows a notification when a focus session is broken/failed early.
     * Why: Alerts the user that the session has terminated and the penalty is charged.
     */
    fun showSessionBreakNotification() {
        val title = context.getString(R.string.notification_session_break_title)
        val body = context.getString(R.string.notification_session_break_body)
        showNotification(
            SESSION_NOTIFICATION_ID,
            NotificationChannels.SESSION_CHANNEL_ID,
            title,
            body,
            NotificationCompat.PRIORITY_HIGH
        )
    }

    /**
     * Shows a notification when auto top-up finishes successfully.
     * Why: Confirms the monetary addition to the user's wallet.
     *
     * @param amountText Readable deposit amount, e.g., "₹200".
     */
    fun showAutoTopUpSuccessNotification(amountText: String) {
        val title = context.getString(R.string.notification_auto_topup_success_title)
        val body = context.getString(R.string.notification_auto_topup_success_body, amountText)
        showNotification(
            WALLET_NOTIFICATION_ID,
            NotificationChannels.WALLET_CHANNEL_ID,
            title,
            body,
            NotificationCompat.PRIORITY_DEFAULT
        )
    }

    /**
     * Shows a notification when auto top-up fails.
     * Why: Alerts the user that the charge failed and they must manually top up their wallet.
     */
    fun showAutoTopUpFailureNotification() {
        val title = context.getString(R.string.notification_auto_topup_failure_title)
        val body = context.getString(R.string.notification_auto_topup_failure_body)
        showNotification(
            WALLET_NOTIFICATION_ID,
            NotificationChannels.WALLET_CHANNEL_ID,
            title,
            body,
            NotificationCompat.PRIORITY_DEFAULT
        )
    }

    /**
     * Shows a notification when the daily auto top-up cap is reached.
     * Why: Warns the user that no more auto charges will occur today to protect their funds.
     */
    fun showDailyCapNotification() {
        val title = context.getString(R.string.notification_daily_cap_title)
        val body = context.getString(R.string.notification_daily_cap_body)
        showNotification(
            WALLET_NOTIFICATION_ID,
            NotificationChannels.WALLET_CHANNEL_ID,
            title,
            body,
            NotificationCompat.PRIORITY_DEFAULT
        )
    }

    /**
     * Internal helper to build and dispatch a notification.
     * Why: Consolidates common notification-building parameters to avoid boilerplate code.
     */
    private fun showNotification(
        notificationId: Int,
        channelId: String,
        title: String,
        body: String,
        priority: Int
    ) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // System standard info icon
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(createContentIntent())
            .build()

        notificationManager.notify(notificationId, notification)
    }
}

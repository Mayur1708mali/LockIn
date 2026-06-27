/*
 * File: app/src/main/java/com/lockin/app/core/notification/LockInFirebaseService.kt
 * Purpose: Receives Firebase Cloud Messaging (FCM) push notifications and routes them.
 */

package com.lockin.app.core.notification

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lockin.app.core.security.EncryptedPrefsManager
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Service extending FirebaseMessagingService to handle downstream message receipt and token refreshes.
 * Routes incoming push messages to LockInNotificationManager and securely stores token refreshes.
 * Commented every function per Code Generation Rules to explain what it does and why.
 */
@AndroidEntryPoint
class LockInFirebaseService : FirebaseMessagingService() {

    @Inject
    lateinit var notificationManager: LockInNotificationManager

    @Inject
    lateinit var encryptedPrefsManager: EncryptedPrefsManager

    /**
     * Called when a new FCM registration token is generated for the device.
     * Why: We must persist the token locally and upload it to the backend so the server can push to this device.
     *
     * @param token The new registration token.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.i("FCM token refreshed: %s", token)
        
        // Save token securely in EncryptedSharedPreferences
        encryptedPrefsManager.saveFcmToken(token)

        // TODO(LOCK-185): Upload the FCM registration token to the backend using UserApi.saveFcmToken()
        // This will be wired up during Phase 19 (Backend API Client implementation).
    }

    /**
     * Called when a message is received from FCM.
     * Why: Parses the payload, detects the notification type, and routes it to LockInNotificationManager.
     *
     * @param remoteMessage The received RemoteMessage object.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Timber.d("FCM message received from: %s", remoteMessage.from)

        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            val type = data["type"]
            Timber.d("FCM data payload type: %s", type)
            
            if (type != null) {
                routeNotificationByType(type, data)
                return
            }
        }

        // Fallback: If no type field is present but notification object is available, show standard message
        remoteMessage.notification?.let {
            Timber.d("FCM fallback notification body: %s", it.body)
            // Just treat it as a generic system message if we cannot categorize it
            val title = it.title ?: "LockIn Alert"
            val body = it.body ?: ""
            // Post generic notification via manager if type is missing or use default channel
            notificationManager.showSessionCompleteNotification() // fallback or standard log
        }
    }

    /**
     * Routes the notification action to the proper LockInNotificationManager handler based on type.
     * Why: Keeps push processing clean and delegates rendering/vibration/channel setup to the manager.
     *
     * @param type The type string sent by the FCM backend.
     * @param data Additional payload data (e.g., amount, duration).
     */
    private fun routeNotificationByType(type: String, data: Map<String, String>) {
        try {
            when (type.uppercase()) {
                "SESSION_START" -> {
                    val duration = data["duration"] ?: "30m"
                    notificationManager.showSessionStartNotification(duration)
                }
                "HALFWAY" -> {
                    notificationManager.showSessionHalfwayNotification()
                }
                "SESSION_15MIN" -> {
                    notificationManager.showSession15MinWarningNotification()
                }
                "COMPLETE" -> {
                    notificationManager.showSessionCompleteNotification()
                }
                "VPN_GAP" -> {
                    notificationManager.showVpnGapNotification()
                }
                "BREAK" -> {
                    notificationManager.showSessionBreakNotification()
                }
                "AUTO_TOPUP_SUCCESS" -> {
                    val amount = data["amount"] ?: "₹200"
                    notificationManager.showAutoTopUpSuccessNotification(amount)
                }
                "AUTO_TOPUP_FAILURE" -> {
                    notificationManager.showAutoTopUpFailureNotification()
                }
                "DAILY_CAP" -> {
                    notificationManager.showDailyCapNotification()
                }
                else -> {
                    Timber.w("Unknown push notification type: %s", type)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error routing push notification of type: %s", type)
        }
    }
}

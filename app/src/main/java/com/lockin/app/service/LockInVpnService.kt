/*
 * File: com/lockin/app/service/LockInVpnService.kt
 * Purpose: Android VpnService implementation enforcing focus session detox blocks.
 * Runs a local TUN interface routing and dropping traffic, whitelisting payments/emergency apps,
 * and showing a persistent foreground service countdown updated down to the second.
 */

package com.lockin.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.lockin.app.MainActivity
import com.lockin.app.core.domain.usecase.CompleteSessionUseCase
import com.lockin.app.core.domain.usecase.LogSessionEventUseCase
import com.lockin.app.core.notification.NotificationChannels
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.FileInputStream
import java.io.IOException
import javax.inject.Inject

/**
 * Foreground VpnService that captures device traffic to implement the detox lock.
 */
@AndroidEntryPoint
class LockInVpnService : VpnService() {

    @Inject
    lateinit var completeSessionUseCase: CompleteSessionUseCase

    @Inject
    lateinit var logSessionEventUseCase: LogSessionEventUseCase

    @Inject
    lateinit var allowlistManager: AllowlistManager

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var countdownJob: Job? = null
    private var packetReaderThread: Thread? = null

    private var vpnInterface: ParcelFileDescriptor? = null
    private var activeSessionId: String? = null

    companion object {
        const val ACTION_START = "com.lockin.app.service.START_VPN"
        const val ACTION_STOP = "com.lockin.app.service.STOP_VPN"
        
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_DURATION_MS = "extra_duration_ms"

        private const val NOTIFICATION_ID = 9001

        private val _remainingTimeFlow = MutableStateFlow<Long>(0L)
        /**
         * StateFlow emitting the remaining duration of the active session in seconds.
         */
        val remainingTimeFlow: StateFlow<Long> = _remainingTimeFlow.asStateFlow()

        private val _isServiceRunning = MutableStateFlow(false)
        /**
         * StateFlow indicating if the VPN service is currently running.
         */
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        Timber.i("LockInVpnService created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Timber.i("LockInVpnService onStartCommand with action: $action")

        if (action == ACTION_START) {
            val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: "unknown_session"
            val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 0L)
            
            if (durationMs > 0 && !_isServiceRunning.value) {
                activeSessionId = sessionId
                setupVpn(durationMs, sessionId)
            }
        } else if (action == ACTION_STOP) {
            stopVpnService()
        }

        // Standard behavior: service is not sticky, but watchdog handles auto-restores
        return START_NOT_STICKY
    }

    /**
     * Configures the VPN builder interface, establishes the TUN socket,
     * starts the background discarding packet reader, and runs the countdown timer.
     */
    private fun setupVpn(durationMs: Long, sessionId: String) {
        Timber.i("Establishing VPN for session $sessionId with duration $durationMs ms")

        serviceScope.launch {
            // 1. Gather all whitelisted packages
            val allowedApps = allowlistManager.getFullAllowlist()
            Timber.d("Packages bypassing VPN: $allowedApps")

            // 2. Build VPN configurations
            val builder = Builder()
                .setSession("LockInLocalDetox")
                .setMtu(1500)
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0) // Capture all IPv4 traffic
                .addRoute("::", 0)      // Capture all IPv6 traffic
                .addDnsServer("8.8.8.8")
                .setBlocking(true)

            // 3. Exclude whitelisted apps
            for (pkg in allowedApps) {
                try {
                    builder.addDisallowedApplication(pkg)
                } catch (e: PackageManager.NameNotFoundException) {
                    Timber.w("Disallowed package not installed: $pkg")
                } catch (e: Exception) {
                    Timber.e(e, "Error adding disallowed application: $pkg")
                }
            }

            // 4. Establish TUN Interface
            try {
                vpnInterface = builder.establish()
                if (vpnInterface != null) {
                    Timber.i("VPN interface established successfully.")
                    startPacketReader(vpnInterface!!)
                    
                    // 5. Start foreground and countdown
                    _isServiceRunning.value = true
                    startForegroundNotification(durationMs)
                    startCountdown(durationMs, sessionId)
                } else {
                    Timber.e("VPN interface establish returned null. Stopping service.")
                    stopVpnService()
                }
            } catch (e: Exception) {
                Timber.e(e, "Fatal error establishing VPN connection")
                stopVpnService()
            }
        }
    }

    /**
     * Spawns a background thread to read from the TUN interface descriptor input stream.
     * Discards the packets immediately to achieve local traffic blockage without queue backlog.
     */
    private fun startPacketReader(pfd: ParcelFileDescriptor) {
        packetReaderThread?.interrupt()
        packetReaderThread = Thread({
            val fd = pfd.fileDescriptor
            val inputStream = FileInputStream(fd)
            val buffer = ByteArray(32768)
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val read = inputStream.read(buffer)
                    if (read < 0) {
                        break // EOF or closed descriptor
                    }
                    // Discard packets
                }
            } catch (e: IOException) {
                Timber.d("VPN socket closed or read error: ${e.message}")
            } catch (e: Exception) {
                Timber.e(e, "Exception in packet reader thread")
            } finally {
                try {
                    pfd.close()
                } catch (ex: Exception) {
                    Timber.e(ex, "Error closing file descriptor")
                }
            }
        }, "LockInVpnReader").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    /**
     * Initializes and shows the persistent notification with the specialUse type if running API 34+.
     */
    private fun startForegroundNotification(durationMs: Long) {
        val initialText = formatRemainingTime(durationMs / 1000)
        val notification = createNotification(initialText)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Runs the countdown timer ticking every second, updates the notification display,
     * emits HEARTBEAT events every 30 seconds, and completes the session on timer expiry.
     */
    private fun startCountdown(durationMs: Long, sessionId: String) {
        countdownJob?.cancel()
        val endTime = System.currentTimeMillis() + durationMs

        countdownJob = serviceScope.launch {
            var tickCount = 0L
            while (System.currentTimeMillis() < endTime) {
                val remainingMs = endTime - System.currentTimeMillis()
                val remainingSeconds = (remainingMs + 999) / 1000 // ceiling division to seconds
                
                _remainingTimeFlow.value = remainingSeconds
                val text = formatRemainingTime(remainingSeconds)
                updateNotification(text)

                // Emit heartbeats every 30 seconds (Phase 9.9)
                if (tickCount > 0 && tickCount % 30 == 0L) {
                    try {
                        logSessionEventUseCase(sessionId, "HEARTBEAT")
                        Timber.d("Focus session heartbeat logged.")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to log heartbeat event")
                    }
                }

                delay(1000)
                tickCount++
            }

            _remainingTimeFlow.value = 0L
            Timber.i("Focus session countdown finished. Committing session completion.")
            
            // Auto complete session inside DB
            try {
                val completeResult = completeSessionUseCase()
                if (completeResult.isSuccess) {
                    Timber.i("Focus session completed successfully in database.")
                } else {
                    Timber.e("Focus session completion database commit failed: ${completeResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception invoking CompleteSessionUseCase on timer finish")
            } finally {
                stopVpnService()
            }
        }
    }

    /**
     * Formats seconds count into down-to-seconds format: "LockIn active · HH:MM:SS remaining".
     */
    private fun formatRemainingTime(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("LockIn active · %02d:%02d:%02d remaining", hours, minutes, seconds)
    }

    /**
     * Helper function to instantiate a Notification object with action intents.
     */
    private fun createNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NotificationChannels.SESSION_CHANNEL_ID)
            .setContentTitle("LockIn Detox Session Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock) // Mono lock icon
            .setColor(0xFFFF3B30.toInt()) // Flat Accent Red Color #FF3B30
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    /**
     * Updates the text contents of the active foreground notification.
     */
    private fun updateNotification(contentText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(contentText))
    }

    /**
     * Tears down the VPN tunnel, terminates the thread and scope, and resets states.
     */
    private fun stopVpnService() {
        Timber.i("Stopping LockInVpnService...")
        
        countdownJob?.cancel()
        packetReaderThread?.interrupt()
        
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Timber.e(e, "Error closing VPN descriptor")
        }
        
        vpnInterface = null
        packetReaderThread = null
        activeSessionId = null

        _isServiceRunning.value = false
        _remainingTimeFlow.value = 0L

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Timber.i("LockInVpnService destroyed.")
    }
}

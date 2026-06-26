/*
 * File: com/lockin/app/service/BootReceiver.kt
 * Purpose: BroadcastReceiver intercepting device boot-ups to restore active detox sessions.
 * Triggers the SessionWatchdog schedule if an active session is found in Room.
 */

package com.lockin.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lockin.app.core.domain.repository.SessionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Listens for system boot completed broadcasts.
 * If a focus session was running before the reboot, schedules the watchdog to restart it.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var sessionRepository: SessionRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Timber.i("BootReceiver received action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            val pendingResult = goAsync()
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val activeSession = sessionRepository.getActiveSession()
                    if (activeSession != null) {
                        Timber.w("Active session (${activeSession.sessionId}) detected after boot. Rescheduling SessionWatchdog.")
                        SessionWatchdog.scheduleWatchdog(context)
                    } else {
                        Timber.d("No active session detected after boot.")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error checking active session during boot restoration.")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

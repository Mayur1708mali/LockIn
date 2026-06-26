/*
 * File: com/lockin/app/service/SessionWatchdog.kt
 * Purpose: JobService watchdog monitoring LockInVpnService liveness.
 * Regularly inspects the database active session status and forces VPN restarts
 * if a gap is detected, logging "VPN_GAP" audit trails.
 */

package com.lockin.app.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lockin.app.core.domain.repository.SessionRepository
import com.lockin.app.core.domain.usecase.LogSessionEventUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Recurrent background watchdog service implemented via JobService to survive process deaths.
 * Monitors VPN state during active focus sessions.
 */
@AndroidEntryPoint
class SessionWatchdog : JobService() {

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var logSessionEventUseCase: LogSessionEventUseCase

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val JOB_ID = 10001
        private const val LATENCY_MS = 30000L // 30s check frequency

        /**
         * Schedules a one-shot watchdog execution to fire after 30 seconds.
         */
        fun scheduleWatchdog(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val component = ComponentName(context, SessionWatchdog::class.java)
            val builder = JobInfo.Builder(JOB_ID, component)
                .setMinimumLatency(LATENCY_MS)
                .setOverrideDeadline(LATENCY_MS + 10000L) // Execute within 40 seconds max
                .setPersisted(true)
            
            val result = scheduler.schedule(builder.build())
            if (result == JobScheduler.RESULT_SUCCESS) {
                Timber.d("SessionWatchdog scheduled successfully.")
            } else {
                Timber.e("Failed to schedule SessionWatchdog.")
            }
        }

        /**
         * Cancels any active scheduled watchdog jobs.
         */
        fun cancelWatchdog(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            scheduler.cancel(JOB_ID)
            Timber.d("SessionWatchdog cancelled.")
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        Timber.d("SessionWatchdog execution triggered.")
        
        serviceScope.launch {
            try {
                val activeSession = sessionRepository.getActiveSession()
                if (activeSession != null) {
                    val isVpnRunning = LockInVpnService.isServiceRunning.value
                    if (!isVpnRunning) {
                        Timber.w("Watchdog check failed: VPN service is not running during active session!")
                        
                        // Log VPN gap audit trail event (Phase 9.8)
                        logSessionEventUseCase(
                            sessionId = activeSession.sessionId,
                            eventType = "VPN_GAP",
                            metadata = "VPN tunnel was killed or crashed. Watchdog restoring connection."
                        )

                        // Restore VPN service
                        val remainingMs = activeSession.targetEndTime - System.currentTimeMillis()
                        if (remainingMs > 0) {
                            val vpnIntent = Intent(this@SessionWatchdog, LockInVpnService::class.java).apply {
                                action = LockInVpnService.ACTION_START
                                putExtra(LockInVpnService.EXTRA_SESSION_ID, activeSession.sessionId)
                                putExtra(LockInVpnService.EXTRA_DURATION_MS, remainingMs)
                            }
                            
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(vpnIntent)
                            } else {
                                startService(vpnIntent)
                            }
                        }
                    } else {
                        Timber.d("Watchdog check passed: VPN service is running healthy.")
                    }

                    // Reschedule for next 30 seconds loop
                    scheduleWatchdog(this@SessionWatchdog)
                } else {
                    Timber.d("No active session in database. Stopping watchdog loop.")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error executing SessionWatchdog logic.")
            } finally {
                // Return false to tell Android we are finished with this execution block
                jobFinished(params, false)
            }
        }

        // Return true to indicate we have background coroutine processing running
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Timber.d("SessionWatchdog stopped prematurely by OS.")
        serviceScope.cancel()
        return true // Reschedule if system constraints stopped the job
    }
}

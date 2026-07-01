/*
 * File: com/lockin/app/feature/session/ActiveSessionScreen.kt
 * Purpose: Composable screen rendering the active focus session countdown.
 * Automatically initiates the LockInVpnService on display, handles keep-screen-on flags,
 * and displays penalty risk labels with a warning message and early-exit triggers.
 */

package com.lockin.app.feature.session

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lockin.app.core.domain.model.SessionStatus
import com.lockin.app.service.LockInVpnService
import com.lockin.app.service.SessionWatchdog
import com.lockin.app.ui.components.LockInButton
import com.lockin.app.ui.components.SectionHeader
import timber.log.Timber

/**
 * Screen displaying the active detox focus session.
 *
 * @param sessionId The session identifier.
 * @param onNavigateToBreakGate Callback when the user clicks 'End Early'.
 * @param onSessionFinished Callback when the session terminates (either completed or broken).
 * @param viewModel Injected viewmodel for timer state tracking.
 */
@Composable
fun ActiveSessionScreen(
    sessionId: String,
    onNavigateToBreakGate: (sessionId: String, penaltyAmountPaise: Int) -> Unit,
    onSessionFinished: (sessionId: String, status: SessionStatus) -> Unit,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val finishedSession by viewModel.finishedSession.collectAsState()

    // 1. Keep the screen awake during the session (Phase 10.8)
    val activity = context as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // 2. Observe if the session completes or breaks, then navigate (Phase 10.6 & Phase 11.6)
    LaunchedEffect(finishedSession) {
        val session = finishedSession
        if (session != null) {
            Timber.i("ActiveSessionScreen: session lifecycle finished with status ${session.status}")
            onSessionFinished(session.sessionId, session.status)
        }
    }

    // 3. Guarantee that the VPN foreground service and watchdog run when this screen is active (Phase 10.7)
    val isVpnRunning by LockInVpnService.isServiceRunning.collectAsState()
    LaunchedEffect(uiState, isVpnRunning) {
        if (uiState is SessionUiState.Success && !isVpnRunning) {
            val success = uiState as SessionUiState.Success
            Timber.d("ActiveSessionScreen: Starting VPN service for session ${success.sessionId}")
            val vpnIntent = Intent(context, LockInVpnService::class.java).apply {
                action = LockInVpnService.ACTION_START
                putExtra(LockInVpnService.EXTRA_SESSION_ID, success.sessionId)
                putExtra(LockInVpnService.EXTRA_DURATION_MS, success.remainingSeconds * 1000)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(vpnIntent)
            } else {
                context.startService(vpnIntent)
            }
            // Start Session watchdog
            SessionWatchdog.scheduleWatchdog(context)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0D0D0D) // Enforce background #0D0D0D
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is SessionUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFFFF3B30) // Accent Red
                    )
                }
                is SessionUiState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Title
                        SectionHeader(
                            label = "ACTIVE FOCUS LOCK",
                            title = "YOU ARE LOCKED IN"
                        )

                        Spacer(modifier = Modifier.height(64.dp))

                        // Monospace Large Countdown (Phase 10.4)
                        CountdownTimer(remainingSeconds = state.remainingSeconds)

                        Spacer(modifier = Modifier.height(32.dp))

                        // Penalty amount Stakes Display
                        Text(
                            text = "PENALTY RISK: ₹${state.penaltyAmountPaise / 100}",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF3B30), // Accent red
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Warnings card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1C1C1E)) // Surface Color
                                .border(1.dp, Color(0xFF48484A), RoundedCornerShape(8.dp)) // Outline color
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "VPN FIREWALL ACTIVE",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF9500) // Amber warning
                                )
                                Text(
                                    text = "All outgoing network connections are blocked except allowed payment gateways and emergency apps. Closing the app will not pause the timer.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF8E8E93) // Muted onSurface
                                )
                            }
                        }

                        // Push early exit below fold (Phase 10.5)
                        Spacer(modifier = Modifier.height(120.dp))

                        LockInButton(
                            text = "End Early",
                            onClick = {
                                onNavigateToBreakGate(state.sessionId, state.penaltyAmountPaise)
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Proceeding will execute the 3-step friction verification.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8E8E93)
                        )
                    }
                }
                is SessionUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "SESSION ERROR",
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF3B30)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No active session is currently running. Please return to dashboard and try again.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFF5F5F7)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        LockInButton(
                            text = "RETURN HOME",
                            onClick = { viewModel.loadActiveSession() }
                        )
                    }
                }
            }
        }
    }
}

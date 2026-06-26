/*
 * File: com/lockin/app/feature/session/BreakGateScreen.kt
 * Purpose: Render the 3-step high-friction detox break-early gate.
 * Handles warning timers, biometric authentication checks, and typed all-caps confirmation matches.
 */

package com.lockin.app.feature.session

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lockin.app.core.domain.model.SessionStatus
import com.lockin.app.service.LockInVpnService
import com.lockin.app.service.SessionWatchdog
import com.lockin.app.ui.components.LockInButton
import com.lockin.app.ui.components.LockInTextField
import com.lockin.app.ui.components.SectionHeader
import timber.log.Timber

/**
 * Custom Warning triangle icon path drawn manually via Compose vector API.
 */
private val AlertIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Alert",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(1f, 21f)
            horizontalLineTo(23f)
            lineTo(12f, 2f)
            close()
            moveTo(12f, 18f)
            curveTo(11.4f, 18f, 11f, 17.6f, 11f, 17f)
            curveTo(11f, 16.4f, 11.4f, 16f, 12f, 16f)
            curveTo(12.6f, 16f, 13f, 16.4f, 13f, 17f)
            curveTo(13f, 17.6f, 12.6f, 18f, 12f, 18f)
            close()
            moveTo(13f, 14f)
            horizontalLineTo(11f)
            verticalLineTo(10f)
            horizontalLineTo(13f)
            close()
        }
    }.build()

/**
 * Screen rendering the early detox exit verification steps.
 *
 * @param sessionId The active session ID.
 * @param penaltyAmountPaise The penalty amount in paise.
 * @param onSessionFinished Callback to trigger navigation to SessionComplete with outcomes.
 * @param onNavigateBack Callback to return to ActiveSession if exit is aborted.
 * @param viewModel ViewModel processing the state steps.
 */
@Composable
fun BreakGateScreen(
    sessionId: String,
    penaltyAmountPaise: Int,
    onSessionFinished: (sessionId: String, status: SessionStatus) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: BreakGateViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val uiState by viewModel.uiState.collectAsState()

    // 1. Initialize viewmodel values on launch
    LaunchedEffect(sessionId, penaltyAmountPaise) {
        viewModel.initSession(sessionId, penaltyAmountPaise)
    }

    // 2. Observe break completion and clean up VPN locks before exiting
    LaunchedEffect(uiState.isBreakSuccess) {
        if (uiState.isBreakSuccess) {
            Timber.i("BreakGateScreen: Session broken early. Stopping VPN service.")
            
            // Stop VPN
            val stopVpnIntent = Intent(context, LockInVpnService::class.java).apply {
                action = LockInVpnService.ACTION_STOP
            }
            context.startService(stopVpnIntent)
            
            // Cancel Watchdog liveness
            SessionWatchdog.cancelWatchdog(context)

            onSessionFinished(sessionId, SessionStatus.BROKEN)
        }
    }

    // 3. Auto-trigger biometric prompt on step transition (Phase 11.4)
    LaunchedEffect(uiState.currentStep) {
        if (uiState.currentStep == BreakStep.BIOMETRIC && activity != null) {
            if (viewModel.isBiometricAvailable()) {
                viewModel.triggerBiometricPrompt(activity)
            }
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Monospace setup header
                SectionHeader(
                    label = "DETOX BREAK ATTEMPT",
                    title = "EARLY BREAK GATE"
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Error message display
                if (uiState.error != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF3B30).copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFFF3B30), RoundedCornerShape(8.dp))
                            .padding(bottom = 24.dp)
                    ) {
                        Text(
                            text = uiState.error!!,
                            color = Color(0xFFFF3B30),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Render current step layout
                when (uiState.currentStep) {
                    BreakStep.WARNING -> {
                        StepWarningLayout(
                            penaltyAmountPaise = uiState.penaltyAmountPaise,
                            secondsLeft = uiState.warningSecondsLeft,
                            onProceed = { viewModel.moveToBiometric() },
                            onCancel = onNavigateBack
                        )
                    }
                    BreakStep.BIOMETRIC -> {
                        StepBiometricLayout(
                            activity = activity,
                            penaltyAmountPaise = uiState.penaltyAmountPaise,
                            isBiometricAvailable = viewModel.isBiometricAvailable(),
                            onTriggerPrompt = { activity?.let { viewModel.triggerBiometricPrompt(it) } },
                            onSimulateSuccess = { viewModel.onBiometricSuccess() },
                            onCancel = { viewModel.onBiometricFailureOrCancel("User cancelled verification") }
                        )
                    }
                    BreakStep.CONFIRMATION -> {
                        StepConfirmationLayout(
                            penaltyAmountPaise = uiState.penaltyAmountPaise,
                            confirmationText = uiState.confirmationText,
                            isBreaking = uiState.isBreaking,
                            onTextChange = { viewModel.updateConfirmationText(it) },
                            onConfirm = { viewModel.confirmBreak() },
                            onCancel = onNavigateBack
                        )
                    }
                }
            }
        }
    }
}

/**
 * Step 1 warning details countdown screen.
 */
@Composable
private fun StepWarningLayout(
    penaltyAmountPaise: Int,
    secondsLeft: Int,
    onProceed: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Color(0xFFFF3B30).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFFF3B30), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AlertIcon,
                contentDescription = "Warning",
                tint = Color(0xFFFF3B30),
                modifier = Modifier.size(32.dp)
            )
        }

        Text(
            text = "STEP 1 OF 3: STAKES WARNING",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF9500)
        )

        Text(
            text = "₹${penaltyAmountPaise / 100}",
            style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Black,
            color = Color(0xFFFF3B30)
        )

        Text(
            text = "If you break now, the penalty stakes of ₹${penaltyAmountPaise / 100} will be permanently deducted from your wallet balance and NOT returned.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFF5F5F7),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Countdown or CTA
        if (secondsLeft > 0) {
            Text(
                text = "FORCED WAIT: ${secondsLeft}S REMAINING",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8E8E93)
            )
        }

        LockInButton(
            text = "Continue",
            enabled = secondsLeft <= 0,
            onClick = onProceed
        )

        LockInButton(
            text = "Cancel Early Exit",
            enabled = true,
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Step 2 biometric authorization screen layout.
 */
@Composable
private fun StepBiometricLayout(
    activity: FragmentActivity?,
    penaltyAmountPaise: Int,
    isBiometricAvailable: Boolean,
    onTriggerPrompt: () -> Unit,
    onSimulateSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "STEP 2 OF 3: BIOMETRIC AUTHENTICATION",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF9500)
        )

        Text(
            text = "BIOMETRIC CHECK REQUIRED",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF5F5F7)
        )

        Text(
            text = "Confirm identity to process the early exit charge of ₹${penaltyAmountPaise / 100}. LockIn security rules enforce biometric verification.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8E8E93),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isBiometricAvailable) {
            LockInButton(
                text = "Open Biometric Prompt",
                onClick = onTriggerPrompt
            )
            LockInButton(
                text = "Abort and Return",
                onClick = onCancel
            )
        } else {
            // Emulator / Developer bypass banner (Phase 11.4 fallback)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2C2C2E))
                    .border(1.dp, Color(0xFFFF9500), RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "DEVELOPER BYPASS MODE",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9500)
                    )
                    Text(
                        text = "Device biometric scanner is not enrolled or unavailable. You can simulate biometric success for testing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF5F5F7),
                        textAlign = TextAlign.Center
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            LockInButton(
                                text = "Simulate",
                                onClick = onSimulateSuccess
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            LockInButton(
                                text = "Cancel",
                                onClick = onCancel
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Step 3 typed text verification screen layout.
 */
@Composable
private fun StepConfirmationLayout(
    penaltyAmountPaise: Int,
    confirmationText: String,
    isBreaking: Boolean,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "STEP 3 OF 3: TYPED CONFIRMATION",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF9500)
        )

        Text(
            text = "TYPE BREAK TO CONFIRM",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF5F5F7)
        )

        Text(
            text = "You must type the word BREAK in all-caps exactly to confirm early session termination. This will immediately consume ₹${penaltyAmountPaise / 100} permanently.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8E8E93),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Flat monospace text input (Phase 11.5)
        LockInTextField(
            value = confirmationText,
            onValueChange = onTextChange,
            placeholder = "Type BREAK here"
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isBreaking) {
            CircularProgressIndicator(color = Color(0xFFFF3B30))
        } else {
            LockInButton(
                text = "Confirm Charge & Break",
                enabled = confirmationText == "BREAK",
                onClick = onConfirm
            )

            LockInButton(
                text = "Cancel and Return",
                enabled = true,
                onClick = onCancel
            )
        }
    }
}

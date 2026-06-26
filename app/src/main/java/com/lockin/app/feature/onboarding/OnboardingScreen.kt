/*
 * File: com/lockin/app/feature/onboarding/OnboardingScreen.kt
 * Purpose: Onboarding screen composed of a 7-step wizard layout with progress indicators,
 * permission dialog launchers (VPN, Notifications), mock payment simulator, and auto top-up toggles.
 */

package com.lockin.app.feature.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lockin.app.ui.components.LockInButton
import com.lockin.app.ui.components.LoadingOverlay
import com.lockin.app.ui.components.SectionHeader
import timber.log.Timber

/**
 * Onboarding screen root container. Enforces 7-step wizard layout and collects states.
 *
 * @param onComplete Callback invoked when onboarding configurations are saved.
 * @param viewModel Injected OnboardingViewModel coordinating states.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Trigger navigation on onboarding completion flag change
    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) {
            onComplete()
        }
    }

    // Launchers for system permissions
    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val granted = VpnService.prepare(context) == null
        viewModel.setVpnPermissionGranted(granted)
        if (granted) {
            Timber.d("VPN permission granted via system dialogue.")
            viewModel.nextStep()
        } else {
            Timber.w("VPN permission denied.")
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setNotificationPermissionGranted(isGranted)
        Timber.d("Notification permission request outcome: $isGranted")
        viewModel.nextStep()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D)) // Background color #0D0D0D
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Progress indicators at the top
            OnboardingProgressIndicator(currentStep = uiState.currentStep)

            // 2. Animated step content switcher
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = uiState.currentStep,
                    transitionSpec = {
                        fadeIn() with fadeOut()
                    }
                ) { step ->
                    when (step) {
                        1 -> StepConcept(onNext = { viewModel.nextStep() })
                        2 -> StepWalletExplainer(onNext = { viewModel.nextStep() })
                        3 -> StepVpnPermission(
                            context = context,
                            onGrant = {
                                val intent = VpnService.prepare(context)
                                if (intent != null) {
                                    vpnLauncher.launch(intent)
                                } else {
                                    viewModel.setVpnPermissionGranted(true)
                                    viewModel.nextStep()
                                }
                            }
                        )
                        4 -> StepNotificationPermission(
                            onGrant = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.setNotificationPermissionGranted(true)
                                    viewModel.nextStep()
                                }
                            },
                            onSkip = {
                                viewModel.setNotificationPermissionGranted(false)
                                viewModel.nextStep()
                            }
                        )
                        5 -> StepFirstDeposit(
                            uiState = uiState,
                            onAmountSelected = { viewModel.updateDepositAmount(it) },
                            onInitiateDeposit = {
                                viewModel.startDeposit()
                            },
                            onSuccessMock = { paymentId -> viewModel.handleDepositSuccess(paymentId) },
                            onFailureMock = { error -> viewModel.handleDepositFailure(error) }
                        )
                        6 -> StepAutoTopUpConfig(
                            uiState = uiState,
                            onToggle = { viewModel.toggleAutoTopUp(it) },
                            onThresholdSelected = { viewModel.updateAutoTopUpThreshold(it) },
                            onAmountSelected = { viewModel.updateAutoTopUpAmount(it) },
                            onConfirm = { viewModel.nextStep() }
                        )
                        7 -> StepReady(
                            uiState = uiState,
                            onComplete = { viewModel.saveAutoTopUpConfigAndComplete() }
                        )
                    }
                }
            }

            // 3. Back navigation button at the bottom fold
            if (uiState.currentStep in 2..6 && uiState.currentStep != 5) {
                LockInButton(
                    text = "Back",
                    onClick = { viewModel.previousStep() },
                    isSecondary = true,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }

        // Global loading overlay during deposit update execution
        LoadingOverlay(isLoading = uiState.isDepositProcessing)
    }
}

/**
 * Centered progress indicators showing dots for each step.
 */
@Composable
private fun OnboardingProgressIndicator(
    currentStep: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..7) {
            Box(
                modifier = Modifier
                    .size(if (i == currentStep) 10.dp else 6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (i == currentStep) Color(0xFFFF3B30) else Color(0xFF48484A)
                    )
            )
            if (i < 7) {
                Spacer(modifier = Modifier.width(12.dp))
            }
        }
    }
}

@Composable
private fun StepConcept(onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        SectionHeader(label = "DETOX", title = "YOUR FOCUS, ON THE LINE.")
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "LockIn enforces a local-only network detox by blocking all internet traffic.\n\n" +
                   "To start, you lock a penalty balance from your wallet. " +
                   "Complete your target session to retrieve it. " +
                   "Fail, and the funds are permanently lost.\n\n" +
                   "No shortcuts. No exceptions.",
            color = Color(0xFFF5F5F7), // OnSurface
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 24.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        LockInButton(text = "Start Configuration", onClick = onNext)
    }
}

@Composable
private fun StepWalletExplainer(onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        SectionHeader(label = "WALLET", title = "HOW FUNDS WORK")
        Spacer(modifier = Modifier.height(24.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ExplainerRow(title = "DEPOSIT", desc = "Add money securely to available balance.")
                HorizontalDivider(color = Color(0xFF2C2C2E), modifier = Modifier.padding(vertical = 12.dp))
                ExplainerRow(title = "SESSION HOLD", desc = "Penalty is held and locked during active sessions.")
                HorizontalDivider(color = Color(0xFF2C2C2E), modifier = Modifier.padding(vertical = 12.dp))
                ExplainerRow(title = "SUCCESS", desc = "Funds returned to wallet available balance.")
                HorizontalDivider(color = Color(0xFF2C2C2E), modifier = Modifier.padding(vertical = 12.dp))
                ExplainerRow(title = "PENALTY", desc = "Breaking early deducts funds permanently.")
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        LockInButton(text = "I Understand", onClick = onNext)
    }
}

@Composable
private fun ExplainerRow(title: String, desc: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = Color(0xFFFF3B30), // Accent Red
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = desc,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8E8E93) // OnSurfaceMuted
        )
    }
}

@Composable
private fun StepVpnPermission(context: Context, onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        SectionHeader(label = "NETWORK LOCK", title = "ACTIVATE LOCAL VPN")
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "LockIn requires permission to launch a local virtual network (VPN).\n\n" +
                   "This connection routes traffic locally into a black hole TUN interface. " +
                   "It blocks internet access across all apps except whitelisted emergency and payment services.\n\n" +
                   "No telemetry or network data is collected or sent from your device.",
            color = Color(0xFFF5F5F7),
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 24.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        LockInButton(text = "Grant VPN Permission", onClick = onGrant)
    }
}

@Composable
private fun StepNotificationPermission(onGrant: () -> Unit, onSkip: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        SectionHeader(label = "ALERTS", title = "POST NOTIFICATIONS")
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Permit notifications to receive persistent countdown alerts during active sessions.\n\n" +
                   "Notifications alert you if the local VPN is disrupted (VPN GAP) or if auto top-up actions complete securely in the background.",
            color = Color(0xFFF5F5F7),
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 24.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        LockInButton(text = "Grant Permissions", onClick = onGrant)
        Spacer(modifier = Modifier.height(12.dp))
        LockInButton(text = "Skip For Now", onClick = onSkip, isSecondary = true)
    }
}

@Composable
private fun StepFirstDeposit(
    uiState: OnboardingUiState,
    onAmountSelected: (Int) -> Unit,
    onInitiateDeposit: () -> Unit,
    onSuccessMock: (String) -> Unit,
    onFailureMock: (String) -> Unit
) {
    var showMockPaymentDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        SectionHeader(label = "WALLET DEPOSIT", title = "INITIAL FUNDING")
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Before starting, fund your LockIn wallet. Sessions cannot be initiated with ₹0 on the line.",
            color = Color(0xFFF5F5F7),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Preset amount selection chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val presets = listOf(10000, 20000, 50000) // ₹100, ₹200, ₹500
            presets.forEach { paise ->
                val amountRs = paise / 100
                val isSelected = uiState.depositAmountPaise == paise
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) Color(0xFFFF3B30) else Color(0xFF1C1C1E))
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Color(0xFFFF3B30) else Color(0xFF48484A),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { onAmountSelected(paise) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "₹$amountRs",
                        color = if (isSelected) Color(0xFF0D0D0D) else Color(0xFFF5F5F7),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (uiState.depositError != null) {
            Text(
                text = "Payment Error: ${uiState.depositError}",
                color = Color(0xFFFF3B30),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        LockInButton(
            text = "Secure Checkout (₹${uiState.depositAmountPaise / 100})",
            onClick = {
                onInitiateDeposit()
                showMockPaymentDialog = true
            }
        )
    }

    // Razorpay Integration mock dialog (to support emulator validation prior to Phase 14)
    if (showMockPaymentDialog) {
        AlertDialog(
            onDismissRequest = { showMockPaymentDialog = false },
            title = { Text(text = "Razorpay Mock Payment Gateway", fontWeight = FontWeight.Bold) },
            text = { Text(text = "Simulate payment checkout for ₹${uiState.depositAmountPaise / 100}.00") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMockPaymentDialog = false
                        onSuccessMock("pay_mock_${System.currentTimeMillis()}")
                    }
                ) {
                    Text("SIMULATE SUCCESS", color = Color(0xFF34C759))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMockPaymentDialog = false
                        onFailureMock("Payment cancelled by user")
                    }
                ) {
                    Text("SIMULATE FAILURE", color = Color(0xFFFF3B30))
                }
            },
            containerColor = Color(0xFF1C1C1E),
            titleContentColor = Color(0xFFF5F5F7),
            textContentColor = Color(0xFF8E8E93),
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
private fun StepAutoTopUpConfig(
    uiState: OnboardingUiState,
    onToggle: (Boolean) -> Unit,
    onThresholdSelected: (Int) -> Unit,
    onAmountSelected: (Int) -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        SectionHeader(label = "AUTO TOP-UP", title = "AUTOMATIC FUNDING")
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Maintain active session continuity. Toggling this enables background payment silent-charges when available balance drops below threshold.",
            color = Color(0xFF8E8E93),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Switch to toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1C1C1E))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Enable Auto Top-Up",
                color = Color(0xFFF5F5F7),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = uiState.autoTopUpEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFF5F5F7),
                    checkedTrackColor = Color(0xFFFF3B30),
                    uncheckedThumbColor = Color(0xFF8E8E93),
                    uncheckedTrackColor = Color(0xFF2C2C2E)
                )
            )
        }

        if (uiState.autoTopUpEnabled) {
            Spacer(modifier = Modifier.height(16.dp))

            // Threshold configuration
            Text(
                text = "TRIGGER THRESHOLD",
                color = Color(0xFF8E8E93),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                val thresholds = listOf(10000, 20000) // ₹100, ₹200
                thresholds.forEach { paise ->
                    val isSelected = uiState.autoTopUpThresholdPaise == paise
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) Color(0xFFFF3B30) else Color(0xFF1C1C1E))
                            .clickable { onThresholdSelected(paise) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "₹${paise / 100}",
                            color = if (isSelected) Color(0xFF0D0D0D) else Color(0xFFF5F5F7),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Charge amount configuration
            Text(
                text = "TOP-UP CHARGE AMOUNT",
                color = Color(0xFF8E8E93),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                val amounts = listOf(20000, 50000) // ₹200, ₹500
                amounts.forEach { paise ->
                    val isSelected = uiState.autoTopUpAmountPaise == paise
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) Color(0xFFFF3B30) else Color(0xFF1C1C1E))
                            .clickable { onAmountSelected(paise) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "₹${paise / 100}",
                            color = if (isSelected) Color(0xFF0D0D0D) else Color(0xFFF5F5F7),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        LockInButton(text = "Confirm Configuration", onClick = onConfirm)
    }
}

@Composable
private fun StepReady(
    uiState: OnboardingUiState,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        SectionHeader(label = "DETOX READY", title = "YOU'RE FULLY SET UP")
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Your first deposit was recorded successfully.\n\n" +
                   "Your configuration, permissions, and payment setup are complete.",
            color = Color(0xFFF5F5F7),
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 24.sp
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Balance confirmation card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "WALLET BALANCE",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = Color(0xFF8E8E93)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "₹${uiState.depositAmountPaise / 100}.00",
                    style = MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.Monospace),
                    color = Color(0xFF34C759), // Success green
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        LockInButton(text = "Start Focus Sessions", onClick = onComplete)
    }
}

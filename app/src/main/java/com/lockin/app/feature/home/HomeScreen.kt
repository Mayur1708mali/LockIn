/*
 * File: com/lockin/app/feature/home/HomeScreen.kt
 * Purpose: Main dashboard layout for the LockIn application.
 * Integrates wallet badges, preset configuration pickers, allowlist previews,
 * streak metrics cards, root detection alerts, and triggers focus session startups.
 */

package com.lockin.app.feature.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lockin.app.ui.components.LoadingOverlay
import com.lockin.app.ui.components.LockInButton
import com.lockin.app.ui.components.SectionHeader
import com.lockin.app.ui.components.WalletBadge

/**
 * Custom Warning triangle icon path drawn manually via Compose vector API.
 */
private val WarningIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Warning",
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
 * Main application dashboard screen.
 * Collects state from [HomeViewModel] and renders user metrics, pickers,
 * and handles starting sessions or automatic top-ups.
 *
 * @param onNavigateToActiveSession Callback to navigate to ActiveSession (sends ID, duration, penalty).
 * @param onNavigateToSettings Callback to open the settings screen.
 * @param onNavigateToWallet Callback to open the wallet screen.
 * @param modifier Layout modifiers.
 * @param viewModel Injected viewmodel for state operations.
 */
@Composable
fun HomeScreen(
    onNavigateToActiveSession: (sessionId: String, durationSeconds: Long, penaltyAmountPaise: Int) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToWallet: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Trigger navigation on session startup success
    LaunchedEffect(uiState.startSessionSuccessId) {
        val successId = uiState.startSessionSuccessId
        if (successId != null) {
            onNavigateToActiveSession(
                successId,
                uiState.selectedDurationMinutes * 60L,
                uiState.selectedPenaltyPaise
            )
            viewModel.clearStartSessionState()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF0D0D0D), // Enforce background #0D0D0D
        topBar = {
            HomeTopBar(
                availableBalancePaise = uiState.wallet?.availableBalance ?: 0,
                autoTopUpEnabled = uiState.isAutoTopUpEnabled,
                onWalletBadgeClick = onNavigateToWallet
            )
        }
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
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 1. Root Warning Banner (Phase 8.9)
                if (uiState.isDeviceRooted) {
                    RootWarningBanner()
                }

                // 2. Setup Header
                SectionHeader(
                    label = "DETOX SETUP",
                    title = "START A FOCUS SESSION"
                )

                // 3. Consecutive streaks card (Phase 8.6)
                StreakCard(streakCount = uiState.streakCount)

                // 4. Duration chip presets and custom picker (Phase 8.3)
                DurationPicker(
                    selectedDurationMinutes = uiState.selectedDurationMinutes,
                    onDurationSelected = { viewModel.updateDuration(it) }
                )

                // 5. Penalty stakes picker capped at wallet balance (Phase 8.4)
                PenaltyPicker(
                    selectedPenaltyPaise = uiState.selectedPenaltyPaise,
                    availableBalancePaise = uiState.wallet?.availableBalance ?: 0,
                    onPenaltySelected = { viewModel.updatePenalty(it) }
                )

                // 6. Allowlisted apps count preview row (Phase 8.5)
                AllowlistPreviewRow(
                    onNavigateToSettings = onNavigateToSettings
                )

                // 7. Insufficient Balance validation warning (Phase 8.7 & 8.8)
                val availableBalance = uiState.wallet?.availableBalance ?: 0
                val penaltyStakes = uiState.selectedPenaltyPaise
                if (availableBalance < penaltyStakes && !uiState.isWalletLoading) {
                    InsufficientBalanceWarning(
                        onAddMoneyClick = onNavigateToWallet,
                        autoTopUpEnabled = uiState.isAutoTopUpEnabled
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 8. Focus start CTA button (Phase 8.10)
                val isLockInEnabled = (availableBalance >= penaltyStakes || uiState.isAutoTopUpEnabled) && !uiState.isWalletLoading
                LockInButton(
                    text = "Lock In",
                    enabled = isLockInEnabled,
                    onClick = { viewModel.onLockInClicked() }
                )

                if (uiState.startSessionError != null) {
                    Text(
                        text = uiState.startSessionError!!,
                        color = Color(0xFFFF3B30), // Accent Red
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Global loading overlay blocking interactions when launching session/auto top-up
            LoadingOverlay(isLoading = uiState.isStartSessionProcessing)
        }
    }
}

/**
 * Custom dashboard top bar displaying app logo and wallet badge metrics.
 */
@Composable
private fun HomeTopBar(
    availableBalancePaise: Int,
    autoTopUpEnabled: Boolean,
    onWalletBadgeClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "LOCKIN",
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Black,
            color = Color(0xFFF5F5F7)
        )

        WalletBadge(
            balancePaise = availableBalancePaise, // Corrected from availableBalance to balancePaise
            autoTopUpEnabled = autoTopUpEnabled,
            modifier = Modifier.clickable { onWalletBadgeClick() }
        )
    }
}

/**
 * Security warning banner surfaced if device root status is detected.
 */
@Composable
private fun RootWarningBanner() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9500).copy(alpha = 0.1f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFFF9500), RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = WarningIcon,
                contentDescription = "Root warning",
                tint = Color(0xFFFF9500),
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = "SECURITY STRIKE",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF9500)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Custom binary signature or root access was detected. Operation continues, but sandbox bypasses might compromise lock controls.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF5F5F7)
                )
            }
        }
    }
}

/**
 * Inline notice when stakes amount exceeds wallet resources.
 */
@Composable
private fun InsufficientBalanceWarning(
    onAddMoneyClick: () -> Unit,
    autoTopUpEnabled: Boolean
) {
    val colorTheme = if (autoTopUpEnabled) Color(0xFF34C759) else Color(0xFFFF3B30)
    Card(
        colors = CardDefaults.cardColors(containerColor = colorTheme.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colorTheme, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (autoTopUpEnabled) "AUTO TOP-UP TRIGGER" else "INSUFFICIENT BALANCE",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold,
                    color = colorTheme
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (autoTopUpEnabled) {
                        "Stakes exceed available funds. Lock In will trigger a silent auto top-up charge of ₹500 automatically."
                    } else {
                        "Stakes exceed available funds. Toggle Auto Top-Up in settings or deposit funds manually to proceed."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF5F5F7)
                )
            }
            if (!autoTopUpEnabled) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "ADD MONEY",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFF3B30),
                    modifier = Modifier
                        .clickable { onAddMoneyClick() }
                        .padding(8.dp)
                )
            }
        }
    }
}

/*
 * File: com/lockin/app/feature/session/SessionCompleteScreen.kt
 * Purpose: Composable screen rendering the post-session outcome dashboard.
 * Supports distinct UI paths for completed sessions (green checks, streak increments)
 * and broken sessions (red warnings, wallet charge statements).
 */

package com.lockin.app.feature.session

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lockin.app.core.domain.model.SessionStatus
import com.lockin.app.ui.components.LockInButton
import com.lockin.app.ui.components.SectionHeader

/**
 * Custom Check Circle vector path drawn manually for session success states.
 */
private val CheckIcon: ImageVector
    get() = ImageVector.Builder(
        name = "CheckIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
            close()
            moveTo(10f, 17f)
            lineTo(5f, 12f)
            lineTo(6.41f, 10.59f)
            lineTo(10f, 14.17f)
            lineTo(17.59f, 6.58f)
            lineTo(19f, 8f)
            lineTo(10f, 17f)
            close()
        }
    }.build()

/**
 * Custom Close/Cancel Circle vector path drawn manually for session broken states.
 */
private val BrokenIcon: ImageVector
    get() = ImageVector.Builder(
        name = "BrokenIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(12f, 2f)
            curveTo(6.47f, 2f, 2f, 6.47f, 2f, 12f)
            curveTo(2f, 17.53f, 6.47f, 22f, 12f, 22f)
            curveTo(17.53f, 22f, 22f, 17.53f, 22f, 12f)
            curveTo(22f, 6.47f, 17.53f, 2f, 12f, 2f)
            close()
            moveTo(17f, 15.59f)
            lineTo(15.59f, 17f)
            lineTo(12f, 13.41f)
            lineTo(8.41f, 17f)
            lineTo(7f, 15.59f)
            lineTo(10.59f, 12f)
            lineTo(7f, 8.41f)
            lineTo(8.41f, 7f)
            lineTo(12f, 10.59f)
            lineTo(15.59f, 7f)
            lineTo(17f, 8.41f)
            lineTo(13.41f, 12f)
            lineTo(17f, 15.59f)
            close()
        }
    }.build()

/**
 * Renders the session results completion layout.
 *
 * @param sessionId Unique session identifier.
 * @param statusString The final outcome status string ("COMPLETED" or "BROKEN").
 * @param onNavigateHome Callback to navigate to the Home screen.
 * @param onNavigateToWallet Callback to navigate to the Wallet screen.
 * @param viewModel ViewModel tracking completion details.
 */
@Composable
fun SessionCompleteScreen(
    sessionId: String,
    statusString: String,
    onNavigateHome: () -> Unit,
    onNavigateToWallet: (openWithdrawalSheet: Boolean) -> Unit,
    viewModel: SessionCompleteViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Initialize session outcome configurations on startup
    LaunchedEffect(sessionId, statusString) {
        viewModel.initSessionResult(sessionId, statusString)
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
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFFFF3B30)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (uiState.status == SessionStatus.COMPLETED) {
                        SuccessLayout(
                            penaltyAmountPaise = uiState.penaltyAmountPaise,
                            streakCount = uiState.streakCount,
                            durationSeconds = uiState.originalDurationSeconds,
                            onLockInAgain = onNavigateHome,
                            onWithdraw = { onNavigateToWallet(true) }
                        )
                    } else {
                        BrokenLayout(
                            penaltyAmountPaise = uiState.penaltyAmountPaise,
                            availableBalancePaise = uiState.availableBalancePaise,
                            onTryAgain = onNavigateHome
                        )
                    }
                }
            }
        }
    }
}

/**
 * Screen layout rendered when focus detox completes successfully.
 */
@Composable
private fun SuccessLayout(
    penaltyAmountPaise: Int,
    streakCount: Int,
    durationSeconds: Long,
    onLockInAgain: () -> Unit,
    onWithdraw: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        SectionHeader(
            label = "DETOX SESSION OVER",
            title = "LOCK IN SUCCESSFUL"
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .background(Color(0xFF34C759).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF34C759), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = CheckIcon,
                contentDescription = "Success checkmark",
                tint = Color(0xFF34C759),
                modifier = Modifier.size(36.dp)
            )
        }

        Text(
            text = "₹${penaltyAmountPaise / 100} returned to your wallet",
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Bold,
            color = Color(0xFF34C759), // Success green #34C759
            textAlign = TextAlign.Center
        )

        Text(
            text = "You successfully completed the detox lock session. The held penalty amount has been released back into your available wallet balance.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8E8E93),
            textAlign = TextAlign.Center
        )

        // Streak status indicator card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1C1C1E))
                .border(1.dp, Color(0xFF48484A), RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "FOCUS STREAK",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0A84FF) // Accent Blue
                )
                Text(
                    text = "Streak: $streakCount ${if (streakCount == 1) "day" else "days"} consecutive",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFF5F5F7)
                )
                Text(
                    text = "Total Focus Session duration: ${formatDuration(durationSeconds)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = Color(0xFF8E8E93)
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        LockInButton(
            text = "Lock In Again",
            onClick = onLockInAgain
        )

        LockInButton(
            text = "Withdraw to Bank",
            onClick = onWithdraw
        )
    }
}

/**
 * Screen layout rendered when focus detox is broken early.
 */
@Composable
private fun BrokenLayout(
    penaltyAmountPaise: Int,
    availableBalancePaise: Int,
    onTryAgain: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        SectionHeader(
            label = "DETOX SESSION OVER",
            title = "SESSION BROKEN EARLY"
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .background(Color(0xFFFF3B30).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFFF3B30), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = BrokenIcon,
                contentDescription = "Detox broken icon",
                tint = Color(0xFFFF3B30),
                modifier = Modifier.size(36.dp)
            )
        }

        Text(
            text = "₹${penaltyAmountPaise / 100} penalty charged",
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF3B30), // Danger red #FF3B30
            textAlign = TextAlign.Center
        )

        Text(
            text = "You exited the focus detox lock before the session completed. The held penalty amount has been permanently deducted from your available wallet balance.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8E8E93),
            textAlign = TextAlign.Center
        )

        // Remaining balance display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1C1C1E))
                .border(1.dp, Color(0xFF48484A), RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "WALLET DETAILS",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF9500) // Amber warning
                )
                Text(
                    text = "Remaining Balance: ₹${availableBalancePaise / 100}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFF5F5F7)
                )
                Text(
                    text = "Streak has been reset to 0 days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF3B30) // Red streak loss alert
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        LockInButton(
            text = "Try Again",
            onClick = onTryAgain
        )
    }
}

/**
 * Formats duration seconds into readable time strings.
 */
private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%dh %dm %ds", hours, minutes, seconds)
    } else if (minutes > 0) {
        String.format("%dm %ds", minutes, seconds)
    } else {
        String.format("%ds", seconds)
    }
}

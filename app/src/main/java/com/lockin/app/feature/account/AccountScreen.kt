/*
 * File: app/src/main/java/com/lockin/app/feature/account/AccountScreen.kt
 * Purpose: Renders the LockIn user profile / Account Screen.
 * Displays user identity details, completed focus statistics, current streak,
 * and handles secure outline Sign Out action with confirmation dialog.
 */

package com.lockin.app.feature.account

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lockin.app.R
import com.lockin.app.ui.components.LoadingOverlay
import java.util.Locale

/**
 * Custom Back Arrow vector asset drawn manually.
 */
private val ArrowBackIcon: ImageVector
    get() = ImageVector.Builder(
        name = "ArrowBack",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(20f, 11f)
            horizontalLineTo(7.8f)
            lineTo(13.4f, 5.4f)
            lineTo(12f, 4f)
            lineTo(4f, 12f)
            lineTo(12f, 20f)
            lineTo(13.4f, 18.6f)
            lineTo(7.8f, 13f)
            horizontalLineTo(20f)
            close()
        }
    }.build()

/**
 * Main Account Composable Screen.
 * Renders user profile information and session metrics.
 *
 * @param onNavigateBack Callback to return to the previous screen.
 * @param onSignOutComplete Callback executed after successfully signing out.
 * @param viewModel Hilt-injected AccountViewModel coordinating state.
 */
@Composable
fun AccountScreen(
    onNavigateBack: () -> Unit,
    onSignOutComplete: () -> Unit,
    viewModel: AccountViewModel = viewModel()
) {
    val totalSessions by viewModel.totalSessions.collectAsState()
    val totalTimeLocked by viewModel.totalTimeLocked.collectAsState()
    val currentStreak by viewModel.currentStreak.collectAsState()
    val signOutState by viewModel.signOutState.collectAsState()

    var showSignOutDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0D0D0D) // Enforce Dark Background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = ArrowBackIcon,
                            contentDescription = "Navigate Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(id = R.string.account_title),
                        color = Color(0xFFF5F5F7),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Centered Profile Section
                val initial = viewModel.displayName.firstOrNull()?.uppercaseChar() ?: 'U'
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFFFF3B30), shape = CircleShape), // Accent Red Background
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = viewModel.displayName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = viewModel.email,
                    color = Color(0xFF8E8E93), // Muted On-Surface Color
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(id = R.string.account_member_since, viewModel.memberSince),
                    color = Color(0xFF8E8E93), // Muted On-Surface Color
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Stats Section: 3 Cards in a Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        label = stringResource(id = R.string.account_stat_sessions),
                        value = totalSessions.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = stringResource(id = R.string.account_stat_time),
                        value = totalTimeLocked,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = stringResource(id = R.string.account_stat_streak),
                        value = "$currentStreak ${if (currentStreak == 1) "DAY" else "DAYS"}",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF2C2C2E), // SurfaceVariant Outline Equivalent
                    thickness = 1.dp
                )

                Spacer(modifier = Modifier.weight(1f))

                // Outline Destructive Sign Out Button
                OutlinedButton(
                    onClick = { showSignOutDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(4.dp), // Enforce 4dp Button radius
                    border = BorderStroke(1.dp, Color(0xFFFF3B30)), // Accent Red Outline
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFFF3B30) // Accent Red content color
                    )
                ) {
                    Text(
                        text = stringResource(id = R.string.account_sign_out).uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Global Loading Indicator during Sign Out processing
            if (signOutState is UiState.Loading) {
                LoadingOverlay(isLoading = true)
            }
        }
    }

    // Confirmation Dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = {
                Text(
                    text = stringResource(id = R.string.sign_out_dialog_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.sign_out_dialog_message),
                    color = Color(0xFF8E8E93),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        viewModel.signOut(onSignOutComplete)
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.sign_out_dialog_confirm),
                        color = Color(0xFFFF3B30), // Accent Red confirm button
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSignOutDialog = false }
                ) {
                    Text(
                        text = stringResource(id = R.string.sign_out_dialog_cancel),
                        color = Color.White
                    )
                }
            },
            containerColor = Color(0xFF1C1C1E), // Surface Background
            shape = RoundedCornerShape(8.dp) // Max 8dp corner radius
        )
    }
}

/**
 * Minimalist Stats Card showing a single focus metric.
 * Why: Unified design for displaying sessions, time locked, or current streak.
 */
@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(1.dp, Color(0xFF48484A), RoundedCornerShape(8.dp)), // Outline color
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)), // Surface color
        shape = RoundedCornerShape(8.dp) // Max 8dp corner radius
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), // Monospace LabelSmall
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = Color(0xFF8E8E93), // Muted color
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

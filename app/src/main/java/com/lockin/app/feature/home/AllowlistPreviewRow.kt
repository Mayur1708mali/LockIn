/*
 * File: com/lockin/app/feature/home/AllowlistPreviewRow.kt
 * Purpose: Composable row showing a summary preview of allowed applications.
 * Dynamically queries the system PackageManager for top app icons, falling back to initials.
 */

package com.lockin.app.feature.home

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

/**
 * Custom ArrowForward vector icon path drawn manually via Compose vector API.
 */
private val ArrowForwardIcon: ImageVector
    get() = ImageVector.Builder(
        name = "ArrowForward",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(12f, 4f)
            lineTo(10.59f, 5.41f)
            lineTo(16.17f, 11f)
            horizontalLineTo(4f)
            verticalLineTo(13f)
            horizontalLineTo(16.17f)
            lineTo(10.59f, 18.59f)
            lineTo(12f, 20f)
            lineTo(20f, 12f)
            close()
        }
    }.build()

/**
 * Data class representing resolved application info.
 */
private data class AppPreviewInfo(
    val packageName: String,
    val label: String,
    val iconDrawable: Drawable?
)

/**
 * Preview summary card for allowed applications (whitelisted UPI/Emergency apps).
 * Shows circular icons of the top 3 apps and a count of remaining apps.
 * Triggers settings navigation on tap.
 *
 * @param onNavigateToSettings Callback when the row is tapped to open settings.
 * @param modifier Layout modifiers.
 */
@Composable
fun AllowlistPreviewRow(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val packageManager = remember { context.packageManager }

    // System whitelisted packages as defined in AGENTS.md
    val defaultAllowlist = listOf(
        "com.google.android.apps.nbu.paisa.user", // GPay
        "com.phonepe.app",                         // PhonePe
        "net.one97.communications",                // Paytm
        "in.org.npci.upiapp",                      // BHIM UPI
        "com.android.emergency"                    // Emergency
    )

    // Load top 3 apps details dynamically on startup
    val topApps = remember {
        defaultAllowlist.take(3).map { pkg ->
            try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                val label = packageManager.getApplicationLabel(appInfo).toString()
                val icon = packageManager.getApplicationIcon(appInfo)
                AppPreviewInfo(pkg, label, icon)
            } catch (e: Exception) {
                // Fallback details if the app isn't installed in emulator environment
                AppPreviewInfo(
                    packageName = pkg,
                    label = when (pkg) {
                        "com.google.android.apps.nbu.paisa.user" -> "GPay"
                        "com.phonepe.app" -> "PhonePe"
                        "net.one97.communications" -> "Paytm"
                        else -> "App"
                    },
                    iconDrawable = null
                )
            }
        }
    }

    val remainingCount = defaultAllowlist.size - topApps.size

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "ALLOWLISTED APPS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToSettings() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Overlapping circular app icons list
                    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                        topApps.forEach { app ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (app.iconDrawable != null) {
                                    Image(
                                        bitmap = app.iconDrawable.toBitmap().asImageBitmap(),
                                        contentDescription = app.label,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Text(
                                        text = app.label.take(1).uppercase(),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary // Accent color
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    val appNamesText = topApps.joinToString { it.label }
                    Text(
                        text = if (remainingCount > 0) "$appNamesText +$remainingCount others" else appNamesText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Icon(
                    imageVector = ArrowForwardIcon,
                    contentDescription = "Edit allowlist",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/*
 * File: com/lockin/app/feature/session/CountdownTimer.kt
 * Purpose: Monospace large-format timer rendering detox countdowns.
 * Features smooth size pulsing animations during the final 60 seconds.
 */

package com.lockin.app.feature.session

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Large format countdown timer display.
 * Displays formatted HH:MM:SS monospace text.
 *
 * @param remainingSeconds The time remaining in seconds.
 * @param modifier Layout modifiers.
 */
@Composable
fun CountdownTimer(
    remainingSeconds: Long,
    modifier: Modifier = Modifier
) {
    val formattedTime = formatTime(remainingSeconds)
    val isUrgent = remainingSeconds in 1..60

    // Set up continuous pulse scaling in the final 60 seconds (Phase 10.4)
    val infiniteTransition = rememberInfiniteTransition(label = "timerPulse")
    val scaleFactor by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isUrgent) 1.08f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Flat red accent for last 60 seconds urgency, else default onSurface
    val textColor = if (isUrgent) Color(0xFFFF3B30) else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier.scale(scaleFactor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formattedTime,
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black
            ),
            color = textColor
        )
    }
}

/**
 * Formats seconds count into "HH:MM:SS".
 */
private fun formatTime(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}

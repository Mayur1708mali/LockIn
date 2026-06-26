/*
 * File: com/lockin/app/feature/home/StreakCard.kt
 * Purpose: Focused streak presentation card.
 * Renders the daily consecutive detox session streaks and displays a 7-day visual calendar grid.
 */

package com.lockin.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Focus Streak Display Card.
 * Displays consecutive focus count and visualizes completion logs over a 7-day grid block.
 *
 * @param streakCount Total consecutive days with completed sessions.
 * @param modifier Layout modifiers.
 */
@Composable
fun StreakCard(
    streakCount: Int,
    modifier: Modifier = Modifier
) {
    // Days of the week labels
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

    // Visualizes history relative to current streak for presentation
    val filledDaysCount = minOf(7, streakCount)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "FOCUS STREAK",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(8.dp), // Max 8dp shape for cards
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (streakCount == 1) "1 DAY" else "$streakCount DAYS",
                            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.ExtraBold,
                            color = if (streakCount > 0) Color(0xFF34C759) else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (streakCount > 0) "CONSECUTIVE FOCUS STREAK ACTIVE" else "NO COMPLETED SESSIONS YET",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 7-day block indicator bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    dayLabels.forEachIndexed { index, day ->
                        // Highlight blocks matching the streak count
                        val isFilled = index >= (7 - filledDaysCount)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isFilled) Color(0xFF34C759) else Color.Transparent
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isFilled) Color(0xFF34C759) else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                            )
                            Text(
                                text = day,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                                color = if (isFilled) Color(0xFF34C759) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

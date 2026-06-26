/*
 * File: com/lockin/app/feature/home/DurationPicker.kt
 * Purpose: Horizontal scrollable presets and a modern Material 3 TimePickerDialog
 * for setting the session detox duration.
 */

package com.lockin.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Composable providing preset duration options (30m, 1h, 2h, 4h, 8h)
 * and a custom TimePickerDialog modal using Material 3 APIs.
 *
 * @param selectedDurationMinutes Currently configured session duration in minutes.
 * @param onDurationSelected Callback invoked when a new duration is configured.
 * @param modifier Layout modifiers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationPicker(
    selectedDurationMinutes: Long,
    onDurationSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = listOf(
        30L to "30M",
        60L to "1H",
        120L to "2H",
        240L to "4H",
        480L to "8H"
    )

    var showTimePickerDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "DETOX DURATION",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(presets) { (minutes, label) ->
                    val isSelected = selectedDurationMinutes == minutes
                    FilterChip(
                        selected = isSelected,
                        onClick = { onDurationSelected(minutes) },
                        label = { Text(text = label) },
                        shape = RoundedCornerShape(4.dp), // 4dp shape as required
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = null
                    )
                }

                item {
                    val isCustom = presets.none { it.first == selectedDurationMinutes }
                    FilterChip(
                        selected = isCustom,
                        onClick = { showTimePickerDialog = true },
                        label = {
                            Text(
                                text = if (isCustom) {
                                    val hours = selectedDurationMinutes / 60
                                    val mins = selectedDurationMinutes % 60
                                    if (hours > 0) "${hours}H ${mins}M" else "${mins}M"
                                } else "CUSTOM"
                            )
                        },
                        shape = RoundedCornerShape(4.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = null
                    )
                }
            }
        }
    }

    if (showTimePickerDialog) {
        val initialHour = (selectedDurationMinutes / 60).toInt()
        val initialMinute = (selectedDurationMinutes % 60).toInt()
        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val totalMinutes = (timePickerState.hour * 60 + timePickerState.minute).toLong()
                        // Enforce a minimum of 1 minute to avoid 0-duration bugs
                        onDurationSelected(maxOf(1L, totalMinutes))
                        showTimePickerDialog = false
                    }
                ) {
                    Text("OK", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTimePickerDialog = false }
                ) {
                    Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            title = {
                Text(
                    text = "SELECT DURATION (HH:MM)",
                    style = MaterialTheme.typography.labelLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(8.dp) // Enforce max 8dp shape for cards/dialogs
        )
    }
}

/*
 * File: com/lockin/app/feature/home/DurationPicker.kt
 * Purpose: Horizontal scrollable presets and a custom classic select minute/hour horizontal layout
 * for setting the session detox duration.
 */

package com.lockin.app.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Composable providing preset duration options (30m, 1h, 2h, 4h, 8h)
 * and a custom TimePickerDialog modal using scrollable column pickers.
 *
 * @param selectedDurationMinutes Currently configured session duration in minutes.
 * @param onDurationSelected Callback invoked when a new duration is configured.
 * @param modifier Layout modifiers.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
        var tempHour by remember { mutableStateOf((selectedDurationMinutes / 60).toInt()) }
        var tempMinute by remember { mutableStateOf((selectedDurationMinutes % 60).toInt()) }

        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val totalMinutes = (tempHour * 60 + tempMinute).toLong()
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
                    text = "SELECT DURATION",
                    style = MaterialTheme.typography.labelLarge
                )
            },
            text = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "HOURS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        WheelPicker(
                            values = (0..23).toList(),
                            selectedValue = tempHour,
                            onValueSelected = { tempHour = it }
                        )
                    }

                    Text(
                        text = ":",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "MINUTES",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        WheelPicker(
                            values = (0..59).toList(),
                            selectedValue = tempMinute,
                            onValueSelected = { tempMinute = it }
                        )
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(8.dp) // Enforce max 8dp shape for cards/dialogs
        )
    }
}

/**
 * Reusable wheel picker component for selecting a single number.
 * Uses a LazyColumn with snap fling behavior to ensure smooth scrolling
 * and exact selection mapping, and highlights the currently selected option.
 *
 * @param values The list of integers to select from.
 * @param selectedValue The current selected value.
 * @param onValueSelected Callback invoked when a value is selected by scrolling or clicking.
 * @param modifier Layout modifiers.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    values: List<Int>,
    selectedValue: Int,
    onValueSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeight = 48.dp
    val displayItems = remember(values) {
        listOf("") + values.map { it.toString().padStart(2, '0') } + listOf("")
    }

    val initialIndex = values.indexOf(selectedValue).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val coroutineScope = rememberCoroutineScope()

    // Sync scroll state to selected value
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (index in values.indices) {
                    onValueSelected(values[index])
                }
            }
    }

    // Sync selected value to scroll state (when changed from outside)
    LaunchedEffect(selectedValue) {
        val targetIndex = values.indexOf(selectedValue).coerceAtLeast(0)
        if (listState.firstVisibleItemIndex != targetIndex && !listState.isScrollInProgress) {
            listState.scrollToItem(targetIndex)
        }
    }

    Box(
        modifier = modifier
            .height(itemHeight * 3)
            .width(80.dp),
        contentAlignment = Alignment.Center
    ) {
        // Highlighting lines for the middle item
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
        }

        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(displayItems) { index, item ->
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .clickable(enabled = item.isNotEmpty()) {
                            coroutineScope.launch {
                                // To center item index `index`, first visible should be `index - 1`
                                listState.animateScrollToItem((index - 1).coerceAtLeast(0))
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val isSelected = (index - 1) == values.indexOf(selectedValue)
                    Text(
                        text = item,
                        style = if (isSelected) {
                            MaterialTheme.typography.titleLarge.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
        }
    }
}

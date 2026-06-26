/*
 * File: com/lockin/app/feature/home/PenaltyPicker.kt
 * Purpose: Horizontal scrollable presets and custom dialog configuration
 * for selecting the focus session penalty stakes. Capped at wallet available balance.
 */

package com.lockin.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lockin.app.ui.components.LockInTextField

/**
 * Composable showing selectable chips for stakes presets (₹50, ₹100, ₹200, ₹500)
 * and a custom input checkout dialogue. Prevents selecting stakes higher than available balance.
 *
 * @param selectedPenaltyPaise Currently selected penalty in Paise.
 * @param availableBalancePaise User's spendable wallet available balance in Paise.
 * @param onPenaltySelected Callback invoked when a new stakes value is configured.
 * @param modifier Layout modifiers.
 */
@Composable
fun PenaltyPicker(
    selectedPenaltyPaise: Int,
    availableBalancePaise: Int,
    onPenaltySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = listOf(
        5000 to "₹50",
        10000 to "₹100",
        20000 to "₹200",
        50000 to "₹500"
    )

    var showCustomDialog by remember { mutableStateOf(false) }
    var customInputVal by remember { mutableStateOf("") }
    var customInputError by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "PENALTY STAKES",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(presets) { (paise, label) ->
                val isSelected = selectedPenaltyPaise == paise
                val isAllowed = paise <= availableBalancePaise

                FilterChip(
                    selected = isSelected,
                    enabled = isAllowed,
                    onClick = { onPenaltySelected(paise) },
                    label = { Text(text = label) },
                    shape = RoundedCornerShape(4.dp), // Enforce 4dp button/chip shapes
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    ),
                    border = null
                )
            }

            item {
                val isCustom = presets.none { it.first == selectedPenaltyPaise }
                FilterChip(
                    selected = isCustom,
                    onClick = {
                        customInputVal = if (isCustom) (selectedPenaltyPaise / 100).toString() else ""
                        customInputError = null
                        showCustomDialog = true
                    },
                    label = {
                        Text(
                            text = if (isCustom) "₹${selectedPenaltyPaise / 100}" else "CUSTOM"
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

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val amountRs = customInputVal.toIntOrNull()
                        if (amountRs == null || amountRs <= 0) {
                            customInputError = "Enter a valid positive number"
                            return@TextButton
                        }
                        val amountPaise = amountRs * 100
                        if (amountPaise < 5000) {
                            customInputError = "Minimum penalty is ₹50"
                            return@TextButton
                        }
                        if (amountPaise > availableBalancePaise) {
                            customInputError = "Stakes exceed wallet balance (₹${availableBalancePaise / 100})"
                            return@TextButton
                        }

                        onPenaltySelected(amountPaise)
                        showCustomDialog = false
                    }
                ) {
                    Text("CONFIRM", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCustomDialog = false }
                ) {
                    Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            title = {
                Text(
                    text = "ENTER CUSTOM STAKES (₹)",
                    style = MaterialTheme.typography.labelLarge
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    LockInTextField(
                        value = customInputVal,
                        onValueChange = {
                            customInputVal = it
                            customInputError = null
                        },
                        placeholder = "Amount in ₹ (min ₹50)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = customInputError != null
                    )
                    if (customInputError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = customInputError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
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

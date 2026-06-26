package com.lockin.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A compact wallet balance badge component.
 * Displays the available balance in Rupees, formatted from Paise.
 * Shows an optional "· AUTO" indicator when auto top-up is enabled.
 *
 * @param balancePaise The available wallet balance in Paise (1 Rupee = 100 Paise).
 * @param autoTopUpEnabled Whether auto top-up is enabled.
 * @param modifier Layout modifiers.
 */
@Composable
fun WalletBadge(
    balancePaise: Int,
    autoTopUpEnabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    val rupees = balancePaise / 100.0
    val formattedBalance = if (balancePaise % 100 == 0) {
        "₹${balancePaise / 100}"
    } else {
        String.format("₹%.2f", rupees)
    }

    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small // 4dp shape
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formattedBalance,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace, // Monospace for financial numbers
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        )

        if (autoTopUpEnabled) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "· AUTO",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary, // Red Accent for active setup
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

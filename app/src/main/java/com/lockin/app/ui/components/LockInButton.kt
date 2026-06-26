package com.lockin.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * A custom button component for the LockIn application.
 * Enforces design system rules: 4dp radius, full-width by default, uppercase text,
 * and distinct primary (red fill) and secondary (outline) variants.
 *
 * @param text The button text label.
 * @param onClick Callback when the button is clicked.
 * @param modifier Layout modifiers.
 * @param enabled Whether the button is active.
 * @param isSecondary If true, renders the secondary outline variant instead of primary fill.
 */
@Composable
fun LockInButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSecondary: Boolean = false
) {
    val uppercaseText = text.uppercase(Locale.getDefault())

    if (isSecondary) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = enabled,
            shape = MaterialTheme.shapes.small, // 4dp radius
            border = BorderStroke(
                width = 1.dp,
                color = if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Text(
                text = uppercaseText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = enabled,
            shape = MaterialTheme.shapes.small, // 4dp radius
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Text(
                text = uppercaseText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

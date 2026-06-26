package com.lockin.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * A unified section header component.
 * Features a small monospace, uppercase label above a bold section title.
 *
 * @param label The category or metadata tag (rendered in uppercase monospace).
 * @param title The main header text of the section.
 * @param modifier Layout modifiers.
 */
@Composable
fun SectionHeader(
    label: String,
    title: String,
    modifier: Modifier = Modifier
) {
    val uppercaseLabel = label.uppercase(Locale.getDefault())

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = uppercaseLabel,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            fontWeight = FontWeight.ExtraBold
        )
    }
}

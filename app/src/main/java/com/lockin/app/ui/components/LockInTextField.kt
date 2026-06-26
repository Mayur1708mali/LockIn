package com.lockin.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily

/**
 * A custom, dark-styled text input component for the LockIn application.
 * Utilized for inputting text confirmation (e.g. typing "BREAK").
 *
 * @param value The text value to show in the text field.
 * @param onValueChange Callback when text changes.
 * @param modifier Layout modifiers.
 * @param label Optional label text.
 * @param placeholder Optional placeholder text.
 * @param isError Mark error status.
 * @param singleLine Ensures single line input.
 * @param keyboardOptions Text field keyboard options.
 * @param keyboardActions Text field keyboard actions.
 */
@Composable
fun LockInTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = label?.let { { Text(text = it) } },
        placeholder = placeholder?.let { { Text(text = it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        isError = isError,
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace // High contrast Monospace for inputs
        ),
        shape = MaterialTheme.shapes.small, // 4dp radius
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            errorContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary, // Red Accent
            unfocusedBorderColor = MaterialTheme.colorScheme.outline, // Gray Outline
            errorBorderColor = MaterialTheme.colorScheme.error,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            errorLabelColor = MaterialTheme.colorScheme.error,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

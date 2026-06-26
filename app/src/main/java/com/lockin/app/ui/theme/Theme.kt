package com.lockin.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Setup dark-only color scheme. Never generate light mode.
private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Background,
    secondary = Outline,
    onSecondary = OnSurface,
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceMuted,
    outline = Outline,
    error = Accent,
    onError = OnSurface
)

/**
 * LockIn theme wrapper. Enforces dark mode only.
 */
@Composable
fun LockInTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

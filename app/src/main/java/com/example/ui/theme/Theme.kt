package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PocketTerminalColorScheme = darkColorScheme(
    primary = PhosphorEmerald,
    onPrimary = ObsidianVoid,
    primaryContainer = PhosphorEmeraldDim,
    onPrimaryContainer = TerminalTextPrimary,
    secondary = CyberCyan,
    onSecondary = ObsidianVoid,
    secondaryContainer = CyberCyanDim,
    onSecondaryContainer = TerminalTextPrimary,
    tertiary = ArgonViolet,
    onTertiary = TerminalTextPrimary,
    tertiaryContainer = ArgonVioletDim,
    onTertiaryContainer = TerminalTextPrimary,
    background = ObsidianVoid,
    onBackground = TerminalTextPrimary,
    surface = TerminalSlate,
    onSurface = TerminalTextPrimary,
    surfaceVariant = TerminalCardElevated,
    onSurfaceVariant = TerminalTextSecondary,
    outline = TerminalBorder,
    error = CrimsonAlert
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PocketTerminalColorScheme,
        typography = Typography,
        content = content
    )
}

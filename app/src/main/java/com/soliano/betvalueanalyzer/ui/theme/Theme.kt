package com.soliano.betvalueanalyzer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BetColorScheme = darkColorScheme(
    primary = Mint,
    onPrimary = Ink,
    primaryContainer = MintDark.copy(alpha = 0.24f),
    onPrimaryContainer = Mint,
    secondary = Violet,
    onSecondary = Ink,
    secondaryContainer = Violet.copy(alpha = 0.20f),
    onSecondaryContainer = Violet,
    tertiary = Blue,
    background = Night,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = Divider,
    error = Danger,
    onError = Ink,
)

@Composable
fun BetValueTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BetColorScheme,
        typography = BetTypography,
        content = content,
    )
}


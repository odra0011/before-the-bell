package com.example.beforethebell.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BellColorScheme = darkColorScheme(
    primary = AntiqueGold,
    onPrimary = NearBlack,
    primaryContainer = DarkSurfaceVariant,
    onPrimaryContainer = AntiqueGoldBright,
    secondary = AntiqueGoldDark,
    onSecondary = TextWarm,
    background = NearBlack,
    onBackground = TextWarm,
    surface = DarkSurface,
    onSurface = TextWarm,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextMuted,
    outline = MetalBorder
)

@Composable
fun BeforeTheBellTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BellColorScheme,
        typography = Typography,
        content = content
    )
}

package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFF4F4F6),
    onPrimary = Color(0xFF0A0B0E),
    primaryContainer = StudioSurfaceElevated,
    onPrimaryContainer = Color(0xFFF4F4F6),
    secondary = CyanVectorPrimary,
    onSecondary = Color(0xFF06202E),
    secondaryContainer = CyanContainerDark,
    onSecondaryContainer = Color(0xFFD0F0FD),
    tertiary = EmeraldNodeOptimized,
    onTertiary = Color(0xFF00291D),
    tertiaryContainer = EmeraldContainerDark,
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = ObsidianBackground,
    onBackground = TextPrimaryDark,
    surface = StudioSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    outline = StudioBorder,
    error = CoralDanger
)

private val LightColorScheme = lightColorScheme(
    primary = CyanPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E8F0),
    onPrimaryContainer = Color(0xFF0F172A),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = EmeraldTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF022C22),
    background = DraftingPaperBg,
    onBackground = TextPrimaryLight,
    surface = DraftingSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = DraftingSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFE2E8F0),
    error = CoralDanger
)

@Composable
fun VectoForgeTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

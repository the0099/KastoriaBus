package com.the09.kastoriabus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = LakeBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF001A54),
    secondary = SunsetOrange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3CC),
    onSecondaryContainer = Color(0xFF3A1600),
    tertiary = Teal,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC9F5EC),
    onTertiaryContainer = Color(0xFF00382F),
    background = Color(0xFFF3F5FA),
    onBackground = Color(0xFF151A26),
    surface = Color(0xFFF3F5FA),
    onSurface = Color(0xFF151A26),
    surfaceVariant = Color(0xFFE1E6F0),
    onSurfaceVariant = Color(0xFF5A6275),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEDF0F7),
    surfaceContainerHigh = Color(0xFFE7EBF3),
    outline = Color(0xFF8E96A8),
    outlineVariant = Color(0xFFD9DEE8),
)

private val DarkColorScheme = darkColorScheme(
    primary = LakeBlueLight,
    onPrimary = Color(0xFF002A78),
    primaryContainer = Color(0xFF0F3AA3),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = SunsetOrangeLight,
    onSecondary = Color(0xFF4D2000),
    secondaryContainer = Color(0xFF6E3300),
    onSecondaryContainer = Color(0xFFFFE3CC),
    tertiary = TealLight,
    onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF005045),
    onTertiaryContainer = Color(0xFFC9F5EC),
    background = Color(0xFF0E1320),
    onBackground = Color(0xFFE2E6F0),
    surface = Color(0xFF0E1320),
    onSurface = Color(0xFFE2E6F0),
    surfaceVariant = Color(0xFF2A3245),
    onSurfaceVariant = Color(0xFFB4BCCC),
    surfaceContainerLowest = Color(0xFF161D2D),
    surfaceContainerLow = Color(0xFF182033),
    surfaceContainer = Color(0xFF1C2438),
    surfaceContainerHigh = Color(0xFF232C42),
    outline = Color(0xFF6E7689),
    outlineVariant = Color(0xFF2E3649),
)

/** Lets any screen ask for the header gradient that matches light/dark mode. */
val LocalHeaderBrush = staticCompositionLocalOf<Brush> { HeaderBrush }

@Composable
fun KastoriaBusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // We don't use Android 12's "dynamic colour" (colours taken from the wallpaper):
    // the app should always look like the Kastoria Bus brand.
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
    ) {
        CompositionLocalProvider(
            LocalHeaderBrush provides if (darkTheme) HeaderBrushDark else HeaderBrush,
            content = content
        )
    }
}

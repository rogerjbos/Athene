package io.github.athene.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// The palette of the first version (Owlingo): teal and indigo on a deep blue-grey.

private val Dark = darkColorScheme(
    primary = Color(0xFF5ED6C8),
    onPrimary = Color(0xFF05302B),
    primaryContainer = Color(0xFF17463F),
    onPrimaryContainer = Color(0xFFB4F1E9),
    secondary = Color(0xFF8B9CFF),
    onSecondary = Color(0xFF111A4A),
    secondaryContainer = Color(0xFF2A3263),
    onSecondaryContainer = Color(0xFFDDE1FF),
    tertiary = Color(0xFFF0B86E),
    onTertiary = Color(0xFF3F2A05),
    background = Color(0xFF141821),
    onBackground = Color(0xFFE8ECF3),
    surface = Color(0xFF141821),
    onSurface = Color(0xFFE8ECF3),
    surfaceVariant = Color(0xFF262D3D),
    onSurfaceVariant = Color(0xFFA9B2C5),
    surfaceContainerLowest = Color(0xFF10131A),
    surfaceContainerLow = Color(0xFF191E29),
    surfaceContainer = Color(0xFF1C212E),
    surfaceContainerHigh = Color(0xFF232A39),
    surfaceContainerHighest = Color(0xFF2C3446),
    outline = Color(0xFF4A5368),
    outlineVariant = Color(0xFF323A4C),
    error = Color(0xFFFF6F59),
    onError = Color(0xFF4A0D03),
    errorContainer = Color(0xFF5A1C12),
    onErrorContainer = Color(0xFFFFDAD3),
)

private val Light = lightColorScheme(
    primary = Color(0xFF0B7F72),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC3F1EB),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4656C8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDEE1FF),
    onSecondaryContainer = Color(0xFF0A1463),
    tertiary = Color(0xFF8A5A0B),
    onTertiary = Color.White,
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF171B24),
    surface = Color(0xFFF5F7FB),
    onSurface = Color(0xFF171B24),
    surfaceVariant = Color(0xFFDFE4EE),
    onSurfaceVariant = Color(0xFF4A5368),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFCFE),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFECEFF5),
    surfaceContainerHighest = Color(0xFFE3E7EF),
    outline = Color(0xFF8B94A8),
    outlineVariant = Color(0xFFCDD3DF),
    error = Color(0xFFC0341D),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD3),
    onErrorContainer = Color(0xFF410500),
)

/** Colours for right answers and streaks, which Material's scheme has no slot for. */
data class OwlColors(
    val good: Color,
    val onGood: Color,
    val goodContainer: Color,
    val onGoodContainer: Color,
    val streak: Color,
)

private val DarkOwl = OwlColors(
    good = Color(0xFF3DDC97),
    onGood = Color(0xFF00301B),
    goodContainer = Color(0xFF12402C),
    onGoodContainer = Color(0xFFB9F5D8),
    streak = Color(0xFFF0B86E),
)

private val LightOwl = OwlColors(
    good = Color(0xFF12804F),
    onGood = Color.White,
    goodContainer = Color(0xFFCDF3DF),
    onGoodContainer = Color(0xFF00301B),
    streak = Color(0xFFC97A0A),
)

val LocalOwlColors = staticCompositionLocalOf { DarkOwl }

@Composable
fun AtheneTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalOwlColors provides if (dark) DarkOwl else LightOwl) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
    }
}

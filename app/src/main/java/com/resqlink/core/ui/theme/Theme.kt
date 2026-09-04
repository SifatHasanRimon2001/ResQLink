package com.resqlink.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.resqlink.domain.model.ThemePreference

private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = Ink,
    primaryContainer = Color(0xFF004E3E),
    onPrimaryContainer = Color(0xFF93F8DB),
    secondary = Cyan,
    onSecondary = Ink,
    background = DeepInk,
    onBackground = Color(0xFFE3F1EF),
    surface = NightSurface,
    onSurface = Color(0xFFE3F1EF),
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = Color(0xFFAAC1C2),
    error = Emergency,
    onError = White,
    outline = Color(0xFF37515C),
)

private val LightColors = lightColorScheme(
    primary = MintDark,
    onPrimary = White,
    primaryContainer = Color(0xFFB5F8E3),
    onPrimaryContainer = Color(0xFF00382C),
    secondary = Color(0xFF006590),
    onSecondary = White,
    background = Mist,
    onBackground = Ink,
    surface = DaySurface,
    onSurface = Ink,
    surfaceVariant = DaySurfaceHigh,
    onSurfaceVariant = Color(0xFF475E64),
    error = EmergencyDark,
    onError = White,
    outline = Color(0xFFB4C8C8),
)

@Composable
fun ResQLinkTheme(
    preference: ThemePreference,
    content: @Composable () -> Unit,
) {
    val dark = when (preference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

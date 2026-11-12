package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color(0xFF002026),
    primaryContainer = CyberCyanContainer,
    onPrimaryContainer = OnCyberCyanContainer,
    secondary = EmeraldConnected,
    onSecondary = Color(0xFF003822),
    secondaryContainer = EmeraldConnectedDark,
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = AmberPending,
    background = DeepBackgroundDark,
    surface = SurfaceCardDark,
    surfaceVariant = SurfaceCardHoverDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
    error = CrimsonError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = CyberCyanLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCF5FB),
    onPrimaryContainer = Color(0xFF002026),
    secondary = EmeraldConnected,
    onSecondary = Color.White,
    background = BackgroundLight,
    surface = SurfaceCardLight,
    surfaceVariant = Color(0xFFE2E8F0),
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    onSurfaceVariant = TextSecondaryLight,
    error = CrimsonError,
    onError = Color.White
)

@Composable
fun CloudSshTheme(
    darkTheme: Boolean = true, // Default to sleek cyber dark theme
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

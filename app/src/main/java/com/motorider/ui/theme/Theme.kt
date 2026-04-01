package com.motorider.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Dark color scheme for motorcycle dashboard.
 * Uses neon accents on dark backgrounds for maximum visibility.
 */
private val DarkColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = DarkBackground,
    primaryContainer = CardBackground,
    onPrimaryContainer = NeonGreen,
    
    secondary = NeonCyan,
    onSecondary = DarkBackground,
    secondaryContainer = CardBackground,
    onSecondaryContainer = NeonCyan,
    
    tertiary = NeonPurple,
    onTertiary = DarkBackground,
    
    error = NeonRed,
    onError = DarkBackground,
    errorContainer = Color(0xFF3D0010),
    onErrorContainer = NeonRed,
    
    background = DarkBackground,
    onBackground = TextPrimary,
    
    surface = CardBackground,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextSecondary,
    
    outline = BorderDefault,
    outlineVariant = DividerColor
)

/**
 * MotoRider theme - always dark for outdoor visibility.
 */
@Composable
fun MotoRiderTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkBackground.toArgb()
            window.navigationBarColor = DarkBackground.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

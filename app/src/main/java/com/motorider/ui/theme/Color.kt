package com.motorider.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * High-contrast neon color palette for motorcycle dashboard.
 * Designed for outdoor visibility and quick glance readability.
 */

// Primary UI Colors
val NeonGreen = Color(0xFF39FF14)      // Safe / Normal state
val NeonYellow = Color(0xFFFFFF00)     // Approaching limit
val NeonOrange = Color(0xFFFF6600)     // Warning state
val NeonRed = Color(0xFFFF073A)        // Critical / Danger state
val NeonCyan = Color(0xFF00FFFF)       // Info / Weather data
val NeonPurple = Color(0xFFBF00FF)     // Accent / Special

// Background Colors
val DarkBackground = Color(0xFF0D0D0D)  // Near-black primary background
val CardBackground = Color(0xFF1A1A1A)  // Elevated card surfaces
val SurfaceDark = Color(0xFF252525)     // Secondary surfaces

// Text Colors
val TextPrimary = Color(0xFFFFFFFF)     // Primary text - pure white
val TextSecondary = Color(0xFFB0B0B0)   // Secondary text
val TextMuted = Color(0xFF707070)       // Muted / disabled text

// Wind Alert Colors
val WindNormal = NeonGreen
val WindWarning = NeonOrange            // 40-60 km/h gusts
val WindDanger = NeonRed                // >60 km/h gusts

// Speed Alert Colors - matching SpeedAlertState
val SpeedNormal = NeonGreen
val SpeedApproaching = NeonYellow
val SpeedWarning = NeonOrange
val SpeedCritical = NeonRed

// UI Element Colors
val BorderDefault = Color(0xFF404040)
val DividerColor = Color(0xFF303030)
val GlowGreen = Color(0x4039FF14)       // Subtle glow effect
val GlowRed = Color(0x40FF073A)         // Warning glow effect

// Night Mode — Dimmed variants (approx 40% brightness)
val NeonGreenDim = Color(0xFF1A7A0A)
val NeonYellowDim = Color(0xFF7A7A00)
val NeonOrangeDim = Color(0xFF7A3300)
val NeonRedDim = Color(0xFF7A041D)
val NeonCyanDim = Color(0xFF007A7A)
val NeonPurpleDim = Color(0xFF5C007A)
val TextPrimaryDim = Color(0xFF999999)
val TextSecondaryDim = Color(0xFF666666)

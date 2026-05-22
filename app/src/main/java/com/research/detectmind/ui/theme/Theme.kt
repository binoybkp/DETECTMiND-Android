package com.research.detectmind.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Slate navy — calm, professional, research-grade
private val LightColors = lightColorScheme(
    primary                = Color(0xFF2C3E6B), // slate navy
    onPrimary              = Color.White,
    primaryContainer       = Color(0xFFDDE3F0), // very light slate tint
    onPrimaryContainer     = Color(0xFF1A2545),
    secondary              = Color(0xFF4A6FA5), // muted steel blue
    onSecondary            = Color.White,
    secondaryContainer     = Color(0xFFDDE8F5),
    onSecondaryContainer   = Color(0xFF1E3A5F),
    tertiary               = Color(0xFF2E7D6B), // muted teal accent
    onTertiary             = Color.White,
    tertiaryContainer      = Color(0xFFCCEDE6),
    onTertiaryContainer    = Color(0xFF0F3D33),
    background             = Color(0xFFF7F8FB), // cool off-white
    onBackground           = Color(0xFF1C1C2E),
    surface                = Color(0xFFFFFFFF),
    onSurface              = Color(0xFF1C1C2E),
    surfaceContainerLow    = Color(0xFFF2F4F8),
    surfaceContainerHigh   = Color(0xFFE8EBF2),
    outline                = Color(0xFFB0B8CC),
    outlineVariant         = Color(0xFFD6DBE8),
    error                  = Color(0xFFC0392B),
    onError                = Color.White,
    errorContainer         = Color(0xFFFDE8E6),
    onErrorContainer       = Color(0xFF7B1D18),
)

private val DarkColors = darkColorScheme(
    primary                = Color(0xFF8BAAD4),
    onPrimary              = Color(0xFF0D1B3E),
    primaryContainer       = Color(0xFF1E2E52),
    onPrimaryContainer     = Color(0xFFD0DCF0),
    secondary              = Color(0xFF7FA8D4),
    onSecondary            = Color(0xFF0D2340),
    secondaryContainer     = Color(0xFF1A334F),
    onSecondaryContainer   = Color(0xFFBDD4EC),
    background             = Color(0xFF0F1117),
    onBackground           = Color(0xFFE2E6F0),
    surface                = Color(0xFF171B24),
    onSurface              = Color(0xFFE2E6F0),
    surfaceContainerLow    = Color(0xFF1C2130),
    error                  = Color(0xFFE57373),
    onError                = Color(0xFF3B0B0B),
    errorContainer         = Color(0xFF4A1414),
    onErrorContainer       = Color(0xFFF5BDBD),
)

@Composable
fun ParticipantMonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}

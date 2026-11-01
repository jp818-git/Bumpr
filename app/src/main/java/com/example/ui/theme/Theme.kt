package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.model.AppTheme
import com.example.model.ThemeMode

private val NeonCyanColorScheme = darkColorScheme(
    primary = NeonCyanPrimary,
    onPrimary = NeonCyanOnPrimary,
    primaryContainer = NeonCyanContainer,
    onPrimaryContainer = NeonCyanOnContainer,
    secondary = NeonCyanSecondary,
    background = NeonCyanBackground,
    surface = NeonCyanSurface,
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFF1F5F9),
    outline = Color(0xFF334155)
)

private val SoundwaveColorScheme = darkColorScheme(
    primary = SoundwavePrimary,
    onPrimary = SoundwaveOnPrimary,
    primaryContainer = SoundwaveContainer,
    onPrimaryContainer = SoundwaveOnContainer,
    secondary = SoundwaveSecondary,
    background = SoundwaveBackground,
    surface = SoundwaveSurface,
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFF8FAFC),
    outline = Color(0xFF3B4861)
)

private val SunsetColorScheme = darkColorScheme(
    primary = SunsetPrimary,
    onPrimary = SunsetOnPrimary,
    primaryContainer = SunsetContainer,
    onPrimaryContainer = SunsetOnContainer,
    secondary = SunsetSecondary,
    background = SunsetBackground,
    surface = SunsetSurface,
    onBackground = Color(0xFFFDE8EE),
    onSurface = Color(0xFFFFF0F5),
    outline = Color(0xFF6B2144)
)

private val CyberpunkColorScheme = darkColorScheme(
    primary = CyberpunkPrimary,
    onPrimary = CyberpunkOnPrimary,
    primaryContainer = CyberpunkContainer,
    onPrimaryContainer = CyberpunkOnContainer,
    secondary = CyberpunkSecondary,
    background = CyberpunkBackground,
    surface = CyberpunkSurface,
    onBackground = Color(0xFFE6F4EA),
    onSurface = Color(0xFFF0FDF4),
    outline = Color(0xFF1B4D3E)
)

private val AmethystColorScheme = darkColorScheme(
    primary = AmethystPrimary,
    onPrimary = AmethystOnPrimary,
    primaryContainer = AmethystContainer,
    onPrimaryContainer = AmethystOnContainer,
    secondary = AmethystSecondary,
    background = AmethystBackground,
    surface = AmethystSurface,
    onBackground = Color(0xFFF3E8FF),
    onSurface = Color(0xFFFAF5FF),
    outline = Color(0xFF581C87)
)

private val TurquoiseColorScheme = darkColorScheme(
    primary = TurqPrimaryDark,
    onPrimary = TurqOnPrimaryDark,
    primaryContainer = TurqPrimaryContainerDark,
    onPrimaryContainer = TurqOnPrimaryContainerDark,
    secondary = TurqSecondaryDark,
    background = TurqBackgroundDark,
    surface = TurqSurfaceDark,
    onBackground = Color(0xFFE0E3E2),
    onSurface = Color(0xFFF0FDF4),
    outline = Color(0xFF4A6360)
)

private val NeonCyanLightColorScheme = lightColorScheme(
    primary = Color(0xFF007A8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC0F4FA),
    onPrimaryContainer = Color(0xFF002025),
    secondary = Color(0xFF0284C7),
    background = Color(0xFFF6F9FA),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    outline = Color(0xFF94A3B8)
)

private val SoundwaveLightColorScheme = lightColorScheme(
    primary = Color(0xFF1E60D0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF3B82F6),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    outline = Color(0xFF94A3B8)
)

private val SunsetLightColorScheme = lightColorScheme(
    primary = Color(0xFFD61869),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD8E7),
    onPrimaryContainer = Color(0xFF3E001B),
    secondary = Color(0xFFE11D48),
    background = Color(0xFFFFF8F9),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1C0D15),
    onSurface = Color(0xFF1C0D15),
    outline = Color(0xFFCBD5E1)
)

private val CyberpunkLightColorScheme = lightColorScheme(
    primary = Color(0xFF059669),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB5FFDE),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF65A30D),
    background = Color(0xFFF5FAF7),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0D1D16),
    onSurface = Color(0xFF0D1D16),
    outline = Color(0xFF94A3B8)
)

private val AmethystLightColorScheme = lightColorScheme(
    primary = Color(0xFF7E22CE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3E8FF),
    onPrimaryContainer = Color(0xFF280747),
    secondary = Color(0xFF9333EA),
    background = Color(0xFFFAF7FD),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF190C28),
    onSurface = Color(0xFF190C28),
    outline = Color(0xFFCBD5E1)
)

private val TurquoiseLightColorScheme = lightColorScheme(
    primary = Color(0xFF008080),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2F0E8),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF0D9488),
    background = Color(0xFFF4F9F8),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0D1C1B),
    onSurface = Color(0xFF0D1C1B),
    outline = Color(0xFF94A3B8)
)

private val AmoledColorScheme = darkColorScheme(
    primary = NeonCyanPrimary,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF152028),
    onPrimaryContainer = NeonCyanPrimary,
    secondary = Color(0xFF94A3B8),
    background = AmoledBackground,
    surface = AmoledSurface,
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color.White,
    outline = Color(0xFF262626)
)

@Composable
fun BumprTheme(
    appTheme: AppTheme = AppTheme.NEON_CYAN,
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemDark
    }

    val colorScheme = if (isDark) {
        when (appTheme) {
            AppTheme.NEON_CYAN -> NeonCyanColorScheme
            AppTheme.SOUNDWAVE -> SoundwaveColorScheme
            AppTheme.SUNSET -> SunsetColorScheme
            AppTheme.CYBERPUNK -> CyberpunkColorScheme
            AppTheme.AMETHYST -> AmethystColorScheme
            AppTheme.TURQUOISE -> TurquoiseColorScheme
            AppTheme.AMOLED -> AmoledColorScheme
        }
    } else {
        when (appTheme) {
            AppTheme.NEON_CYAN -> NeonCyanLightColorScheme
            AppTheme.SOUNDWAVE -> SoundwaveLightColorScheme
            AppTheme.SUNSET -> SunsetLightColorScheme
            AppTheme.CYBERPUNK -> CyberpunkLightColorScheme
            AppTheme.AMETHYST -> AmethystLightColorScheme
            AppTheme.TURQUOISE -> TurquoiseLightColorScheme
            AppTheme.AMOLED -> SoundwaveLightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

package com.grillpoet.lighty.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// 中性灰主题，与 libi 一致。主色用前景色本身，不再有紫色强调。
private val LightColors = lightColorScheme(
    primary = Color(0xFF18191C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF18191C).copy(alpha = 0.08f),
    onPrimaryContainer = Color(0xFF18191C),

    secondary = Color(0xFF5B5C61),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF18191C).copy(alpha = 0.08f),
    onSecondaryContainer = Color(0xFF18191C),

    tertiary = Color(0xFF5B5C61),
    onTertiary = Color.White,

    background = Color(0xFFF6F7F8),
    onBackground = Color(0xFF18191C),

    surface = Color.White,
    onSurface = Color(0xFF18191C),
    surfaceVariant = Color(0xFF18191C).copy(alpha = 0.06f),
    onSurfaceVariant = Color(0xFF5B5C61),

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFB),
    surfaceContainer = Color(0xFFF6F7F8),
    surfaceContainerHigh = Color(0xFFF0F1F3),
    surfaceContainerHighest = Color(0xFFE8E9EC),

    outline = Color(0xFFB8BABF),
    outlineVariant = Color(0xFFE0E1E4),

    error = Color(0xFFD93F3F),
    onError = Color.White,
    errorContainer = Color(0xFFD93F3F).copy(alpha = 0.12f),
    onErrorContainer = Color(0xFFD93F3F)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFEEEEEE),
    onPrimary = Color(0xFF0F0F10),
    primaryContainer = Color(0xFFEEEEEE).copy(alpha = 0.12f),
    onPrimaryContainer = Color(0xFFEEEEEE),

    secondary = Color(0xFFB8B9BD),
    onSecondary = Color(0xFF0F0F10),
    secondaryContainer = Color(0xFFEEEEEE).copy(alpha = 0.12f),
    onSecondaryContainer = Color(0xFFEEEEEE),

    tertiary = Color(0xFFB8B9BD),
    onTertiary = Color(0xFF0F0F10),

    background = Color(0xFF0F0F10),
    onBackground = Color(0xFFEEEEEE),

    surface = Color(0xFF1A1A1C),
    onSurface = Color(0xFFEEEEEE),
    surfaceVariant = Color(0xFFEEEEEE).copy(alpha = 0.08f),
    onSurfaceVariant = Color(0xFFB8B9BD),

    surfaceContainerLowest = Color(0xFF141416),
    surfaceContainerLow = Color(0xFF1A1A1C),
    surfaceContainer = Color(0xFF1F1F22),
    surfaceContainerHigh = Color(0xFF252528),
    surfaceContainerHighest = Color(0xFF2C2C30),

    outline = Color(0xFF4A4B50),
    outlineVariant = Color(0xFF2C2C30),

    error = Color(0xFFE26B6B),
    onError = Color(0xFF0F0F10),
    errorContainer = Color(0xFFE26B6B).copy(alpha = 0.15f),
    onErrorContainer = Color(0xFFE26B6B)
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun LightyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
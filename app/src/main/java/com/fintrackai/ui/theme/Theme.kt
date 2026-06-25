package com.fintrackai.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ─── Design Tokens ──────────────────────────────────────────────────────────

object AppColors {
    // Light palette — clean dark blue fintech
    val LightPrimary = Color(0xFF0F2B46)
    val LightBackground = Color(0xFFF8F9FB)
    val LightCard = Color(0xFFEEF1F5)
    val LightSurface = Color(0xFFFFFFFF)
    val LightText = Color(0xFF0F2B46)
    val LightTextSecondary = Color(0xFF5A6B7F)
    val LightTextTertiary = Color(0xFF8896A6)
    val LightBorder = Color(0xFFDDE3EB)
    val LightSuccess = Color(0xFF059669)
    val LightError = Color(0xFFDC2626)
    val LightWarning = Color(0xFFD97706)
    val LightAccent = Color(0xFF1A6DD4)

    // Dark palette — deep navy, premium feel
    val DarkPrimary = Color(0xFF5B9CF6)
    val DarkBackground = Color(0xFF0A1020)
    val DarkCard = Color(0xFF111B2E)
    val DarkSurface = Color(0xFF162033)
    val DarkText = Color(0xFFF1F5F9)
    val DarkTextSecondary = Color(0xFF8DA2B8)
    val DarkTextTertiary = Color(0xFF5A7089)
    val DarkBorder = Color(0xFF1E2E44)
    val DarkSuccess = Color(0xFF34D399)
    val DarkError = Color(0xFFF87171)
    val DarkWarning = Color(0xFFFBBF24)
    val DarkAccent = Color(0xFF5B9CF6)

    // 12-color chart palette — muted, professional tones for category visualization
    val LightChartColors = listOf(
        Color(0xFF4A7FA5), // slate blue
        Color(0xFF5A9E8A), // sage green
        Color(0xFFB07D62), // warm clay
        Color(0xFF9B7EB8), // dusty violet
        Color(0xFF6A9EB5), // muted sky blue
        Color(0xFFB8965A), // warm amber
        Color(0xFFB36B6B), // muted rose-red
        Color(0xFF6B9E6B), // muted green
        Color(0xFF5B7FB5), // medium blue
        Color(0xFF8B7BAD), // muted purple
        Color(0xFF4E9BAD), // muted teal
        Color(0xFFB57E5A), // warm terracotta
    )
    val DarkChartColors = listOf(
        Color(0xFF6FA3C8), // slate blue (lightened)
        Color(0xFF7ABCA8), // sage green (lightened)
        Color(0xFFCB9E83), // warm clay (lightened)
        Color(0xFFB89DD4), // dusty violet (lightened)
        Color(0xFF8ABFCE), // muted sky blue (lightened)
        Color(0xFFD4B07A), // warm amber (lightened)
        Color(0xFFCE8E8E), // muted rose-red (lightened)
        Color(0xFF8ABE8A), // muted green (lightened)
        Color(0xFF7B9FCE), // medium blue (lightened)
        Color(0xFFAA9DC8), // muted purple (lightened)
        Color(0xFF6EBCCC), // muted teal (lightened)
        Color(0xFFCEA07A), // warm terracotta (lightened)
    )
}

// ─── Spacing System (4dp grid) ──────────────────────────────────────────────

object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 48.dp
}

// ─── Shape System ───────────────────────────────────────────────────────────

object AppShape {
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val extraLarge = 20.dp
    val pill = 100.dp
}

// ─── Extended Color System ──────────────────────────────────────────────────

data class ExtendedColors(
    val card: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val border: Color,
    val success: Color,
    val warning: Color,
    val accent: Color,
    val chartColors: List<Color>,
    val error: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        card = AppColors.LightCard,
        text = AppColors.LightText,
        textSecondary = AppColors.LightTextSecondary,
        textTertiary = AppColors.LightTextTertiary,
        border = AppColors.LightBorder,
        success = AppColors.LightSuccess,
        error = AppColors.LightError,
        warning = AppColors.LightWarning,
        accent = AppColors.LightAccent,
        chartColors = AppColors.LightChartColors
    )
}

// ─── Material 3 Color Schemes ───────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary = AppColors.LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4F5),
    onPrimaryContainer = Color(0xFF0F2B46),
    secondary = AppColors.LightAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E8FA),
    onSecondaryContainer = Color(0xFF0A1F3A),
    tertiary = AppColors.LightSuccess,
    onTertiary = Color.White,
    background = AppColors.LightBackground,
    onBackground = AppColors.LightText,
    surface = AppColors.LightSurface,
    onSurface = AppColors.LightText,
    surfaceVariant = Color(0xFFEDF1F7),
    onSurfaceVariant = AppColors.LightTextSecondary,
    outline = AppColors.LightBorder,
    outlineVariant = Color(0xFFECF0F5),
    error = AppColors.LightError,
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.DarkPrimary,
    onPrimary = Color(0xFF0A1020),
    primaryContainer = Color(0xFF1A3050),
    onPrimaryContainer = Color(0xFFD6E4F5),
    secondary = AppColors.DarkAccent,
    onSecondary = Color(0xFF0A1020),
    secondaryContainer = Color(0xFF1A3050),
    onSecondaryContainer = Color(0xFFD6E4F5),
    tertiary = AppColors.DarkSuccess,
    onTertiary = Color(0xFF003726),
    background = AppColors.DarkBackground,
    onBackground = AppColors.DarkText,
    surface = AppColors.DarkSurface,
    onSurface = AppColors.DarkText,
    surfaceVariant = Color(0xFF1A2840),
    onSurfaceVariant = AppColors.DarkTextSecondary,
    outline = AppColors.DarkBorder,
    outlineVariant = Color(0xFF1A2840),
    error = AppColors.DarkError,
    onError = Color(0xFF690005),
)

// ─── Typography ─────────────────────────────────────────────────────────────

private val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = (-1.0).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.15).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    bodyLarge = TextStyle(letterSpacing = 0.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(letterSpacing = 0.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(letterSpacing = 0.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
)

// ─── Theme ──────────────────────────────────────────────────────────────────

@Composable
fun FinTrackTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) ExtendedColors(
        card = AppColors.DarkCard,
        text = AppColors.DarkText,
        textSecondary = AppColors.DarkTextSecondary,
        textTertiary = AppColors.DarkTextTertiary,
        border = AppColors.DarkBorder,
        success = AppColors.DarkSuccess,
        warning = AppColors.DarkWarning,
        accent = AppColors.DarkAccent,
        chartColors = AppColors.DarkChartColors,
        error = AppColors.DarkError,
    ) else ExtendedColors(
        card = AppColors.LightCard,
        text = AppColors.LightText,
        textSecondary = AppColors.LightTextSecondary,
        textTertiary = AppColors.LightTextTertiary,
        border = AppColors.LightBorder,
        success = AppColors.LightSuccess,
        warning = AppColors.LightWarning,
        accent = AppColors.LightAccent,
        chartColors = AppColors.LightChartColors,
        error = AppColors.LightError,
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}

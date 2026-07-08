package com.borgpharmacy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

private val BorgBlue = Color(0xFF0E4D8F)
private val BorgRed = Color(0xFFC8172B)
private val BorgGray = Color(0xFF6B7280)

private val LightColors: ColorScheme = lightColorScheme(
    primary = BorgBlue,
    secondary = BorgRed,
    tertiary = BorgGray,
    background = Color(0xFFF4F8FC),
    surface = Color.White,
    onPrimary = Color.White,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF8EC5FF),
    secondary = Color(0xFFFF9AA8),
    tertiary = Color(0xFFC8CDD5),
)

// Uses Android's bundled Arabic-capable Noto/Roboto stack, with strong weights and no extra font padding.
// This gives a modern Cairo/Tajawal-like look without increasing APK size with external font files.
private val AppFontFamily = FontFamily.SansSerif
private val AppTypography = Typography().let { base ->
    fun TextStyle.withAppFont() = copy(fontFamily = AppFontFamily)
    Typography(
        displayLarge = base.displayLarge.withAppFont(),
        displayMedium = base.displayMedium.withAppFont(),
        displaySmall = base.displaySmall.withAppFont(),
        headlineLarge = base.headlineLarge.withAppFont(),
        headlineMedium = base.headlineMedium.withAppFont(),
        headlineSmall = base.headlineSmall.withAppFont(),
        titleLarge = base.titleLarge.withAppFont(),
        titleMedium = base.titleMedium.withAppFont(),
        titleSmall = base.titleSmall.withAppFont(),
        bodyLarge = base.bodyLarge.withAppFont(),
        bodyMedium = base.bodyMedium.withAppFont(),
        bodySmall = base.bodySmall.withAppFont(),
        labelLarge = base.labelLarge.withAppFont(),
        labelMedium = base.labelMedium.withAppFont(),
        labelSmall = base.labelSmall.withAppFont(),
    )
}

@Composable
fun BorgPharmacyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

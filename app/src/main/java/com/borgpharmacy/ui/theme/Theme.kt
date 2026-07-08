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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.borgpharmacy.R

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

private val CairoBold = FontFamily(
    Font(R.font.cairo_bold, weight = FontWeight.Bold),
)

private val AppTypography = Typography().let { base ->
    fun TextStyle.withCairoBold() = copy(
        fontFamily = CairoBold,
        fontWeight = FontWeight.Bold,
    )
    Typography(
        displayLarge = base.displayLarge.withCairoBold(),
        displayMedium = base.displayMedium.withCairoBold(),
        displaySmall = base.displaySmall.withCairoBold(),
        headlineLarge = base.headlineLarge.withCairoBold(),
        headlineMedium = base.headlineMedium.withCairoBold(),
        headlineSmall = base.headlineSmall.withCairoBold(),
        titleLarge = base.titleLarge.withCairoBold(),
        titleMedium = base.titleMedium.withCairoBold(),
        titleSmall = base.titleSmall.withCairoBold(),
        bodyLarge = base.bodyLarge.withCairoBold(),
        bodyMedium = base.bodyMedium.withCairoBold(),
        bodySmall = base.bodySmall.withCairoBold(),
        labelLarge = base.labelLarge.withCairoBold(),
        labelMedium = base.labelMedium.withCairoBold(),
        labelSmall = base.labelSmall.withCairoBold(),
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

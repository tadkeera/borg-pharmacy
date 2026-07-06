package com.borgpharmacy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BorgBlue = Color(0xFF0E4D8F)
private val BorgRed = Color(0xFFC8172B)
private val BorgGray = Color(0xFF6B7280)

private val LightColors: ColorScheme = lightColorScheme(
    primary = BorgBlue,
    secondary = BorgRed,
    tertiary = BorgGray,
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    onPrimary = Color.White,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF8EC5FF),
    secondary = Color(0xFFFF9AA8),
    tertiary = Color(0xFFC8CDD5),
)

@Composable
fun BorgPharmacyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}

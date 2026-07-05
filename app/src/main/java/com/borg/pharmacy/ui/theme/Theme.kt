package com.borg.pharmacy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF3F51B5), // Indigo
    onPrimary = Color.White,
    secondary = Color(0xFF009688), // Teal
    onSecondary = Color.White,
    background = Color(0xFFF5F5F5), // White/Light Gray
    onBackground = Color(0xFF121212),
    surface = Color.White,
    onSurface = Color(0xFF121212)
)

@Composable
fun BorgPharmacyTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}

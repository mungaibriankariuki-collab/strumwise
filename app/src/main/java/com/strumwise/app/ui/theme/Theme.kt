package com.strumwise.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val scheme = darkColorScheme(
    primary = StrumColors.Lagoon,
    onPrimary = StrumColors.Espresso,
    primaryContainer = StrumColors.LagoonDeep,
    onPrimaryContainer = StrumColors.Cream,
    secondary = StrumColors.Ember,
    onSecondary = StrumColors.Espresso,
    tertiary = StrumColors.Gold,
    background = StrumColors.Espresso,
    onBackground = StrumColors.Cream,
    surface = StrumColors.EspressoRaised,
    onSurface = StrumColors.Cream,
    surfaceVariant = StrumColors.EspressoHigh,
    onSurfaceVariant = StrumColors.CreamMuted,
    surfaceContainer = StrumColors.EspressoRaised,
    surfaceContainerHigh = StrumColors.EspressoHigh,
    outline = StrumColors.CreamMuted,
    error = StrumColors.Miss,
)

private val display = FontFamily.Serif

private val typography = Typography().let { base ->
    base.copy(
        displayLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 52.sp),
        displayMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 40.sp),
        headlineMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun StrumwiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

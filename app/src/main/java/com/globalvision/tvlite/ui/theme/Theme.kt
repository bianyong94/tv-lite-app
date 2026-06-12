package com.globalvision.tvlite.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TvColorScheme = darkColorScheme(
    background = TvBackground,
    surface = TvSurface,
    surfaceVariant = TvSurfaceVariant,
    secondary = TvAccent,
    tertiary = TvAccentSoft,
    primary = TvPrimary,
    onPrimary = TvOnPrimary,
)

@Composable
fun GlobalVisionTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TvColorScheme,
        typography = TvTypography,
        content = content,
    )
}

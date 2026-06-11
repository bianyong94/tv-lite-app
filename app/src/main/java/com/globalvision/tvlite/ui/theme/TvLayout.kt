package com.globalvision.tvlite.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class TvLayoutMetrics(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val heroHeight: Dp,
    val posterWidth: Dp,
    val railSpacing: Dp,
    val posterColumns: Int,
    val episodeColumns: Int,
    val sourceColumns: Int,
)

@Composable
fun rememberTvLayoutMetrics(): TvLayoutMetrics {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    return remember(screenWidth) {
        when {
            screenWidth >= 2400 -> TvLayoutMetrics(
                horizontalPadding = 72.dp,
                verticalPadding = 30.dp,
                heroHeight = 420.dp,
                posterWidth = 216.dp,
                railSpacing = 18.dp,
                posterColumns = 6,
                episodeColumns = 10,
                sourceColumns = 10,
            )
            screenWidth >= 1600 -> TvLayoutMetrics(
                horizontalPadding = 56.dp,
                verticalPadding = 22.dp,
                heroHeight = 360.dp,
                posterWidth = 196.dp,
                railSpacing = 14.dp,
                posterColumns = 6,
                episodeColumns = 8,
                sourceColumns = 8,
            )
            else -> TvLayoutMetrics(
                horizontalPadding = 32.dp,
                verticalPadding = 24.dp,
                heroHeight = 300.dp,
                posterWidth = 196.dp,
                railSpacing = 16.dp,
                posterColumns = 4,
                episodeColumns = 4,
                sourceColumns = 3,
            )
        }
    }
}

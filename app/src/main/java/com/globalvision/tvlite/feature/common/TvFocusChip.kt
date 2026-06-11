package com.globalvision.tvlite.feature.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.globalvision.tvlite.ui.theme.TvDivider
import com.globalvision.tvlite.ui.theme.TvFocusBorder

@Composable
fun TvFocusChip(
    text: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.large
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.98f)
        focused -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.98f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
    }
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        focused -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = when {
        focused -> TvFocusBorder
        selected -> Color.Transparent
        else -> TvDivider
    }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "tv_chip_scale",
    )
    val elevation by animateFloatAsState(
        targetValue = if (focused) 24f else 0f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 460f),
        label = "tv_chip_elevation",
    )

    Surface(
        onClick = onClick,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = elevation
            }
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused || !selected) 1.5.dp else 0.dp,
                color = borderColor,
                shape = shape,
            ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium,
            ),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            maxLines = 1,
        )
    }
}

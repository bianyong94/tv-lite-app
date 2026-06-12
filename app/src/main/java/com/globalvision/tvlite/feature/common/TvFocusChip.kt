package com.globalvision.tvlite.feature.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.globalvision.tvlite.ui.theme.TvDivider
import com.globalvision.tvlite.ui.theme.TvFocusBorder

@Composable
fun TvFocusChip(
    text: String,
    selected: Boolean = false,
    minWidth: Dp? = null,
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
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "tv_chip_scale",
    )

    Surface(
        onClick = onClick,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .consumeRepeatedDpadEvents()
            .ensureVisibleOnFocus()
            .then(if (minWidth != null) Modifier.widthIn(min = minWidth) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = when {
                    focused -> 2.5.dp
                    !selected -> 1.5.dp
                    else -> 0.dp
                },
                color = borderColor,
                shape = shape,
            ),
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

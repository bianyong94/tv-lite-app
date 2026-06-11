package com.globalvision.tvlite.feature.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import coil.compose.AsyncImage
import com.globalvision.tvlite.core.model.TvPosterItem
import com.globalvision.tvlite.ui.theme.TvDivider
import com.globalvision.tvlite.ui.theme.TvFocusBorder

@Composable
fun TvPosterCard(
    item: TvPosterItem,
    width: androidx.compose.ui.unit.Dp = 220.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val cardShape = MaterialTheme.shapes.medium
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.035f else 1f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
        label = "tv_poster_scale",
    )
    val elevation by animateFloatAsState(
        targetValue = if (focused) 26f else 0f,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 460f),
        label = "tv_poster_elevation",
    )
    Card(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = elevation
            }
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) TvFocusBorder else TvDivider,
                shape = cardShape,
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(cardShape),
            ) {
                AsyncImage(
                    model = item.posterUrl.takeIf { it.isNotBlank() },
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(
                            color = if (focused) {
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                            } else {
                                Color.Transparent
                            },
                        ),
                )
            }
            Spacer(modifier = Modifier.padding(top = 2.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                maxLines = 2,
            )
            if (item.remark.isNotBlank() || item.year.isNotBlank()) {
                Text(
                    text = listOf(item.year, item.remark).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

package com.globalvision.tvlite.feature.history

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.globalvision.tvlite.core.local.TvPlaybackHistoryStore
import com.globalvision.tvlite.core.model.TvPlaybackHistoryEntry
import com.globalvision.tvlite.core.network.TvRepository
import com.globalvision.tvlite.feature.common.TvPosterImage
import com.globalvision.tvlite.feature.common.TvFeedbackPanel
import com.globalvision.tvlite.feature.common.TvScreenScaffold
import com.globalvision.tvlite.feature.common.consumeRepeatedDpadEvents
import com.globalvision.tvlite.feature.common.tvFocusBorder

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenDetail: (TvPlaybackHistoryEntry) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val historyStore = remember(context) { TvPlaybackHistoryStore(context) }
    val repository = remember(context) { TvRepository(context) }
    val firstCardRequester = remember { FocusRequester() }
    var historyItems by remember { mutableStateOf(emptyList<TvPlaybackHistoryEntry>()) }

    BackHandler(onBack = onBack)

    fun reloadHistory() {
        historyItems = historyStore.getAll()
    }

    LaunchedEffect(Unit) {
        reloadHistory()
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner, historyStore) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reloadHistory()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    TvScreenScaffold(
        title = "",
        showTitle = false,
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "历史记录",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Color.White,
                )
            }

        if (historyItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无历史记录",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.78f),
                    ),
                )
            }
            return@TvScreenScaffold
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .focusGroup(),
        ) {
            itemsIndexed(historyItems, key = { _, item -> item.movieId }) { index, item ->
                HistoryPosterCard(
                    repository = repository,
                    item = item,
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(firstCardRequester) else Modifier)
                        .focusProperties { if (index < 5) up = firstCardRequester },
                    onClick = { onOpenDetail(item) },
                )
            }
        }

        LaunchedEffect(historyItems.firstOrNull()?.movieId) {
            if (historyItems.isNotEmpty()) {
                firstCardRequester.requestFocus()
            }
        }
        }
    }
}

@Composable
private fun HistoryPosterCard(
    repository: TvRepository,
    item: TvPlaybackHistoryEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.0f else 1f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
        label = "history_poster_scale",
    )
    val progressLine = buildProgressLine(item)

    Column(
        modifier = modifier
            .consumeRepeatedDpadEvents()
            .zIndex(if (focused) 1f else 0f)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .padding(2.dp),
    ) {
        Card(
            onClick = onClick,
            shape = shape,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.82f)
                .shadow(
                    elevation = if (focused) 10.dp else 0.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.45f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                )
                .tvFocusBorder(focused = focused, shape = shape, width = 2.dp, unfocusedWidth = 0.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F1F)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                TvPosterImage(
                    posterUrl = item.posterUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    movieId = item.movieId,
                    repository = repository,
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.84f)),
                                startY = 240f,
                            ),
                        ),
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = if (focused) FontWeight.ExtraBold else FontWeight.Bold,
                            color = Color.White,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    val subInfo = listOf(item.year, item.category).filter { it.isNotBlank() }.joinToString(" · ")
                    if (subInfo.isNotBlank()) {
                        Text(
                            text = subInfo,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.72f),
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Text(
                        text = progressLine,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.92f),
                        ),
                        maxLines = 1,
                        modifier = if (focused) {
                            Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}

private fun buildProgressLine(item: TvPlaybackHistoryEntry): String {
    val parts = listOf(
        item.sourceName.ifBlank { "源 ${item.sourceIndex + 1}" },
        item.episodeName.ifBlank { "第 ${item.episodeIndex + 1} 集" },
        "播放到 ${formatPlaybackPosition(item.positionMs)}",
    )
    return parts.joinToString(" · ")
}

private fun formatPlaybackPosition(positionMs: Long): String {
    val totalSeconds = (positionMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

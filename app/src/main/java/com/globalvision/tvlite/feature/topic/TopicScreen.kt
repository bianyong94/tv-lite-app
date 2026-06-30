package com.globalvision.tvlite.feature.topic

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.globalvision.tvlite.core.model.TvMovieTopic
import com.globalvision.tvlite.core.model.TvPosterItem
import com.globalvision.tvlite.core.network.TvRepository
import com.globalvision.tvlite.feature.common.TvFeedbackPanel
import com.globalvision.tvlite.feature.common.TvLoadingPanel
import com.globalvision.tvlite.feature.common.TvScreenScaffold
import com.globalvision.tvlite.feature.common.consumeRepeatedDpadEvents
import com.globalvision.tvlite.feature.common.ensureVisibleOnFocus
import kotlinx.coroutines.delay

@Composable
fun TopicScreen(
    repository: TvRepository,
    onOpenDetail: (String) -> Unit,
) {
    var topics by remember { mutableStateOf<List<TvMovieTopic>>(emptyList()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var selectedDetail by remember { mutableStateOf<TvMovieTopic?>(null) }
    var loadingTopics by remember { mutableStateOf(true) }
    var loadingDetail by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    
    val firstTopicFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        loadingTopics = true
        failed = false
        topics = try {
            repository.getMovieTopics()
        } catch (_: Throwable) {
            failed = true
            emptyList()
        }
        selectedIndex = 0
        loadingTopics = false
    }

    LaunchedEffect(topics.size) {
        if (topics.isNotEmpty()) {
            delay(32)
            firstTopicFocusRequester.requestFocus()
        }
    }

    val selectedTopic = topics.getOrNull(selectedIndex)
    LaunchedEffect(selectedTopic?.id) {
        val topic = selectedTopic ?: return@LaunchedEffect
        loadingDetail = true
        selectedDetail = try {
            repository.getMovieTopicDetail(topic.id)
        } catch (_: Throwable) {
            topic
        }
        gridState.scrollToItem(0)
        loadingDetail = false
    }

    TvScreenScaffold(
        title = "",
        showTitle = false,
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp)
        ) {
            when {
                loadingTopics -> TvLoadingPanel(message = "正在加载专题...")
                failed -> TvFeedbackPanel(
                    title = "专题加载失败",
                    message = "当前无法获取专题数据，稍后再试。",
                )
                topics.isEmpty() -> TvFeedbackPanel(
                    title = "暂无专题",
                    message = "当前没有可展示的专题内容。",
                )
                else -> TopicContent(
                    topics = topics,
                    selectedIndex = selectedIndex,
                    detail = selectedDetail ?: selectedTopic,
                    loadingDetail = loadingDetail,
                    gridState = gridState,
                    firstTopicFocusRequester = firstTopicFocusRequester,
                    onSelectTopic = { selectedIndex = it },
                    onOpenDetail = onOpenDetail,
                )
            }
        }
    }
}

@Composable
private fun TopicContent(
    topics: List<TvMovieTopic>,
    selectedIndex: Int,
    detail: TvMovieTopic?,
    loadingDetail: Boolean,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    firstTopicFocusRequester: FocusRequester,
    onSelectTopic: (Int) -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 40.dp),
        modifier = modifier
            .fillMaxSize()
            .focusGroup(),
    ) {
        // 1. 顶部 Hero 海报
        item(span = { GridItemSpan(maxLineSpan) }) {
            TopicHero(topic = detail ?: topics[selectedIndex])
        }

        // 2. 横向专题列表
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
                    .padding(top = 18.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                itemsIndexed(topics, key = { _, item -> item.id }) { index, topic ->
                    TopicRailCard(
                        topic = topic,
                        selected = index == selectedIndex,
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstTopicFocusRequester)
                        } else {
                            Modifier
                        },
                        onClick = { onSelectTopic(index) },
                    )
                }
            }
        }

        // 3. 标题和统计信息
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "专题片单",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    ),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "${detail?.items?.size ?: 0} 个资源",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.55f),
                    ),
                )
            }
        }

        // 4. 网格内容区域
        when {
            loadingDetail && detail?.items.isNullOrEmpty() -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp,
                        )
                    }
                }
            }
            detail?.items.isNullOrEmpty() -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TvFeedbackPanel(
                        title = "暂无专题资源",
                        message = "当前专题暂时没有返回资源内容。",
                    )
                }
            }
            else -> {
                itemsIndexed(detail!!.items, key = { _, item -> item.id }) { _, item ->
                    TopicPosterCard(
                        item = item,
                        onClick = { onOpenDetail(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TopicHero(topic: TvMovieTopic) {
    val shape = RoundedCornerShape(22.dp)
    Card(
        onClick = {},
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111823)),
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = topic.coverUrl.takeIf { it.isNotBlank() },
                contentDescription = topic.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.95f),
                                Color.Black.copy(alpha = 0.65f),
                                Color.Transparent,
                            ),
                            startX = 0f,
                            endX = 1200f
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.65f)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = topic.description.ifBlank { "精选专题片单，按主题整理高相关资源。" },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        lineHeight = 24.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = buildTopicStats(topic),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun TopicRailCard(
    topic: TvMovieTopic,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    
    val borderColor = when {
        focused -> Color.White
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        else -> Color.Transparent
    }

    Card(
        onClick = onClick,
        shape = shape,
        border = BorderStroke(2.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141B25)),
        modifier = Modifier
            .then(modifier)
            .width(260.dp)
            .consumeRepeatedDpadEvents()
            .onFocusChanged { focused = it.isFocused }
            .shadow(
                elevation = if (focused) 12.dp else 0.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.6f),
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.72f),
        ) {
            AsyncImage(
                model = topic.coverUrl.takeIf { it.isNotBlank() },
                contentDescription = topic.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                            startY = 100f,
                        ),
                    ),
            )
            Text(
                text = topic.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TopicPosterCard(
    item: TvPosterItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    
    val borderColor = if (focused) Color.White else Color.Transparent

    Card(
        onClick = onClick,
        shape = shape,
        border = BorderStroke(2.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F1F)),
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .consumeRepeatedDpadEvents()
            .ensureVisibleOnFocus()
            .onFocusChanged { focused = it.isFocused }
            .shadow(
                elevation = if (focused) 12.dp else 0.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.6f),
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f),
        ) {
            AsyncImage(
                model = item.posterUrl.takeIf { it.isNotBlank() },
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            startY = 200f,
                        ),
                    ),
            )
            
            val label = item.label.ifBlank { item.remark }
            if (label.isNotBlank()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    ),
                    modifier = Modifier
                        .padding(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    maxLines = 1,
                )
            }
            
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = if (focused) FontWeight.ExtraBold else FontWeight.Bold,
                    color = Color.White,
                ),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(12.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun buildTopicStats(topic: TvMovieTopic): String {
    val movieCount = topic.movieCount.takeIf { it > 0 } ?: topic.items.size
    val views = topic.viewCount.ifBlank { "0" }
    return "影片 $movieCount   |   浏览 $views+"
}
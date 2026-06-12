package com.globalvision.tvlite.feature.detail

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.globalvision.tvlite.core.model.TvEpisode
import com.globalvision.tvlite.core.model.TvMovieDetail
import com.globalvision.tvlite.core.model.TvPosterItem
import com.globalvision.tvlite.core.network.TvRepository
import com.globalvision.tvlite.feature.common.TvFeedbackPanel
import com.globalvision.tvlite.feature.common.TvFocusChip
import com.globalvision.tvlite.feature.common.TvLoadingPanel
import com.globalvision.tvlite.feature.common.TvScreenScaffold
import com.globalvision.tvlite.feature.common.LocalTvStatusHostState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    repository: TvRepository,
    movieId: String,
    onBack: () -> Unit,
    onPlay: (title: String, movieId: String, sourceIndex: Int, episodeIndex: Int) -> Unit,
) {
    val statusHost = LocalTvStatusHostState.current
    val tag = "DetailScreen"
    var detail by remember { mutableStateOf<TvMovieDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selectedSourceIndex by rememberSaveable(movieId) { mutableStateOf(0) }
    var selectedEpisodeIndex by rememberSaveable(movieId) { mutableStateOf(0) }
    val episodesBySource = remember(movieId) { mutableStateMapOf<String, List<TvEpisode>>() }
    var episodesLoading by remember(movieId) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    // 页面核心首焦Requester
    val playFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    var playButtonFocused by remember { mutableStateOf(false) }

    LaunchedEffect(movieId) {
        Log.d(tag, "load detail start: movieId=$movieId")
        loading = true
        detail = try {
            repository.getDetail(movieId)
        } catch (_: Throwable) {
            Log.e(tag, "load detail failed: movieId=$movieId")
            null
        }
        loading = false
        selectedSourceIndex = 0
        selectedEpisodeIndex = 0
        episodesBySource.clear()
    }

    LaunchedEffect(detail) {
        if (detail != null) {
            // 进入页面后，强控焦点精准落到“播放按钮”上
            delay(32)
            playFocusRequester.requestFocus()
        }
    }

    if (loading && detail == null) {
        TvScreenScaffold(title = "", onBack = null, showTitle = false) {
            TvLoadingPanel(message = "正在加载影片详情与可播放资源...", centered = true)
        }
        return
    }

    val movie = detail
    if (movie == null) {
        TvScreenScaffold(title = "", onBack = null, showTitle = false) {
            TvFeedbackPanel(
                title = "详情加载失败",
                message = "当前影片详情暂时不可用，你可以返回首页重新选择。",
                action = { TvFocusChip(text = "返回", onClick = onBack) },
            )
        }
        return
    }

    val safeSourceIndex = selectedSourceIndex.coerceIn(0, (movie.sources.size - 1).coerceAtLeast(0))
    val currentSource = movie.sources.getOrNull(safeSourceIndex)
    val sourceEpisodes = currentSource?.code?.let { episodesBySource[it] }.orEmpty().ifEmpty { currentSource?.episodes.orEmpty() }
    val safeEpisodeIndex = selectedEpisodeIndex.coerceIn(0, (sourceEpisodes.size - 1).coerceAtLeast(0))
    val currentEpisode = sourceEpisodes.getOrNull(safeEpisodeIndex)
    val playbackEpisode = currentEpisode ?: sourceEpisodes.firstOrNull() ?: currentSource?.episodes?.firstOrNull()

    BackHandler {
        if (playButtonFocused) {
            onBack()
        } else {
            coroutineScope.launch {
                playFocusRequester.requestFocus()
            }
        }
    }

    LaunchedEffect(movie.id, currentSource?.code) {
        val source = currentSource ?: return@LaunchedEffect
        episodesLoading = true
        val fetched = try {
            repository.getEpisodes(movie.id, source.code)
        } catch (throwable: Throwable) {
            emptyList()
        }
        episodesBySource[source.code] = if (fetched.isNotEmpty()) fetched else source.episodes
        selectedEpisodeIndex = 0
        episodesLoading = false
    }

    // 执行播放
    fun executePlay(episodeIdx: Int) {
        val source = currentSource ?: return
        val episodes = episodesBySource[source.code].orEmpty().ifEmpty { source.episodes }
        if (episodes.isEmpty()) return
        val targetEpisode = episodes.getOrNull(episodeIdx.coerceIn(0, episodes.lastIndex)) ?: return
        statusHost?.show("开始播放", "正在进入 ${source.name} · ${targetEpisode.name}")
        onPlay(movie.title, movie.id, safeSourceIndex, episodes.indexOf(targetEpisode).coerceAtLeast(0))
    }

    TvScreenScaffold(
        title = "",
        onBack = null,
        showTitle = false,
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            
            // ================= 顶层：巨幕大字影视头信息 =================
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val eyebrow = listOf(movie.year, movie.area).filter { it.isNotBlank() }.joinToString(" · ")
                if (eyebrow.isNotBlank()) {
                    Text(
                        text = eyebrow,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                    )
                }
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 38.sp, // 大屏黄金清晰度
                        fontWeight = FontWeight.ExtraBold
                    ),
                )
                val subtitle = listOf(
                    movie.remarks,
                    movie.actor.takeIf { it.isNotBlank() }?.let { "主演：$it" },
                ).filterNotNull().joinToString("    ")
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        ),
                    )
                }
            }

            // ================= 中层：海报与简介联动区域 =================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // 【核心修改】海报在详情页作为静态纯展示元素，不再让其接收遥控器焦点，解决焦点向左移动卡死的致命Bug
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .width(220.dp)
                        .aspectRatio(0.7f)
                        .shadow(16.dp, shape = RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161616))
                ) {
                    AsyncImage(
                        model = movie.posterUrl.takeIf { it.isNotBlank() },
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // 右侧：详尽介绍面板与主操作按钮
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                            .padding(24.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            val creationInfo = listOf(movie.director, movie.writer).filter { it.isNotBlank() }.joinToString("   ")
                            if (creationInfo.isNotBlank()) {
                                Text(
                                    text = creationInfo,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                                    color = Color.White.copy(alpha = 0.5f),
                                )
                            }
                            Text(
                                text = movie.content.ifBlank { "暂无简介" },
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 28.sp),
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Text(
                                text = "当前选择  ▶  源 ${safeSourceIndex + 1} / ${movie.sources.size}   ·   " +
                                       "选集 ${if(sourceEpisodes.isNotEmpty()) safeEpisodeIndex + 1 else 0} / ${sourceEpisodes.size}",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // 显式一键主播放按钮（Apple TV 标志性大药丸设计）
                    TvFocusChip(
                        text = if (playbackEpisode != null) "立即播放: ${playbackEpisode.name}" else "开始播放",
                        selected = true,
                        modifier = Modifier
                        .align(Alignment.Start)
                        .height(54.dp)
                        .width(260.dp)
                        .focusRequester(playFocusRequester)
                        .onFocusChanged { playButtonFocused = it.isFocused }, // 页面打开后原生落焦于此
                    onClick = { executePlay(selectedEpisodeIndex) },
                )
                }
            }

            // ================= 线下平铺一：切换播放源版块 =================
            if (movie.sources.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "切换播放源", 
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    )
                    
                    FlowRow(
                        maxItemsInEachRow = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        movie.sources.forEachIndexed { index, source ->
                            TvFocusChip(
                                text = source.name,
                                selected = index == safeSourceIndex,
                                minWidth = 110.dp,
                                onClick = {
                                    selectedSourceIndex = index
                                    selectedEpisodeIndex = 0
                                    statusHost?.show("切换播放源", "正在切换到 ${source.name}")
                                },
                            )
                        }
                    }
                }
            }

            // ================= 线下平铺二：选集版块 =================
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "选集播放", 
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    )
                    if (episodesLoading) {
                        Text(
                            text = "正在同步最新集数...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (sourceEpisodes.isEmpty()) {
                    TvFeedbackPanel(
                        title = "暂无可播放选集",
                        message = "当前播放源集数空空如也，请在上方尝试切换其它片源。",
                    )
                } else {
                    FlowRow(
                        maxItemsInEachRow = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        sourceEpisodes.forEachIndexed { index, episode ->
                            TvFocusChip(
                                text = episode.name,
                                selected = index == safeEpisodeIndex,
                                minWidth = 88.dp,
                                onClick = {
                                    selectedEpisodeIndex = index
                                    executePlay(index)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun TvMovieDetail.toPosterItem() = TvPosterItem(
    id = id,
    title = title,
    posterUrl = posterUrl,
    year = year,
    score = "",
    category = "",
    remark = remarks,
    overview = content,
)

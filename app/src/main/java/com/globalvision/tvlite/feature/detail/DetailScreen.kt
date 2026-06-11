package com.globalvision.tvlite.feature.detail

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.globalvision.tvlite.core.model.TvEpisode
import com.globalvision.tvlite.core.model.TvMovieDetail
import com.globalvision.tvlite.core.model.TvPosterItem
import com.globalvision.tvlite.core.network.TvRepository
import com.globalvision.tvlite.feature.common.TvFeedbackPanel
import com.globalvision.tvlite.feature.common.TvFocusChip
import com.globalvision.tvlite.feature.common.TvLoadingPanel
import com.globalvision.tvlite.feature.common.TvPosterCard
import com.globalvision.tvlite.feature.common.TvScreenScaffold
import com.globalvision.tvlite.feature.common.LocalTvStatusHostState
import com.globalvision.tvlite.ui.theme.rememberTvLayoutMetrics
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    repository: TvRepository,
    movieId: String,
    onBack: () -> Unit,
    onPlay: (title: String, movieId: String, sourceIndex: Int, episodeIndex: Int) -> Unit,
) {
    val layout = rememberTvLayoutMetrics()
    val statusHost = LocalTvStatusHostState.current
    val tag = "DetailScreen"
    var detail by remember { mutableStateOf<TvMovieDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selectedSourceIndex by rememberSaveable(movieId) { mutableStateOf(0) }
    var selectedEpisodeIndex by rememberSaveable(movieId) { mutableStateOf(0) }
    val episodesBySource = remember(movieId) { mutableStateMapOf<String, List<TvEpisode>>() }
    var episodesLoading by remember(movieId) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val playFocusRequester = remember { FocusRequester() }
    val sourceFocusRequester = remember { FocusRequester() }
    val episodeFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

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
            playFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(detail?.id) {
        val movieDetail = detail ?: return@LaunchedEffect
        if (movieDetail.sources.isEmpty()) return@LaunchedEffect

        episodesLoading = true
        var selectedIndex = 0
        var selectedEpisodes = movieDetail.sources.firstOrNull()?.episodes.orEmpty()

        for ((index, source) in movieDetail.sources.withIndex()) {
            val fetchedEpisodes = try {
                repository.getEpisodes(movieDetail.id, source.code)
            } catch (throwable: Throwable) {
                Log.w(tag, "bootstrap episodes failed: movieId=${movieDetail.id} source=${source.code}", throwable)
                source.episodes
            }
            val candidateEpisode = fetchedEpisodes.firstOrNull() ?: continue
            val candidateUrl = repository.resolveEpisodeUrl(candidateEpisode)
            if (repository.isPlayableMediaUrl(candidateUrl)) {
                selectedIndex = index
                selectedEpisodes = fetchedEpisodes
                break
            }
            if (index == 0) {
                selectedEpisodes = fetchedEpisodes
            }
        }

        selectedSourceIndex = selectedIndex
        selectedEpisodeIndex = 0
        if (movieDetail.sources.isNotEmpty()) {
            episodesBySource[movieDetail.sources[selectedIndex].code] = selectedEpisodes
        }
        episodesLoading = false
    }

    if (loading && detail == null) {
        TvScreenScaffold(title = "", onBack = null, showTitle = false) {
            TvLoadingPanel(
                message = "正在加载影片详情与可播放资源...",
                centered = true,
            )
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

    val safeSourceIndex = selectedSourceIndex.coerceIn(
        0,
        (movie.sources.size - 1).coerceAtLeast(0),
    )
    val currentSource = movie.sources.getOrNull(safeSourceIndex)
    val sourceEpisodes = currentSource?.code?.let { episodesBySource[it] }.orEmpty()
        .ifEmpty { currentSource?.episodes.orEmpty() }
    val safeEpisodeIndex = selectedEpisodeIndex.coerceIn(
        0,
        (sourceEpisodes.size - 1).coerceAtLeast(0),
    )
    val currentEpisode = sourceEpisodes.getOrNull(safeEpisodeIndex)
    val playbackEpisode = currentEpisode
        ?: sourceEpisodes.firstOrNull()
        ?: currentSource?.episodes?.firstOrNull()
        ?: movie.sources.firstOrNull()?.episodes?.firstOrNull()

    BackHandler(onBack = onBack)

    LaunchedEffect(movie.id, currentSource?.code) {
        val source = currentSource ?: return@LaunchedEffect
        episodesLoading = true
        val fetched = try {
            repository.getEpisodes(movie.id, source.code)
        } catch (throwable: Throwable) {
            Log.w(tag, "load episodes failed: movieId=${movie.id} source=${source.code}", throwable)
            emptyList()
        }
        episodesBySource[source.code] = if (fetched.isNotEmpty()) fetched else source.episodes
        selectedEpisodeIndex = 0
        episodesLoading = false
    }

    suspend fun playCurrentSelection() {
        val source = currentSource ?: return
        val episodes = episodesBySource[source.code].orEmpty().ifEmpty { source.episodes }
        if (episodes.isEmpty()) return
        val episode = episodes.getOrNull(selectedEpisodeIndex.coerceIn(0, episodes.lastIndex))
            ?: episodes.firstOrNull()
            ?: return
        statusHost?.show("开始播放", "正在进入 ${source.name} · ${episode.name}")
        onPlay(movie.title, movie.id, safeSourceIndex, episodes.indexOf(episode).coerceAtLeast(0))
    }

    TvScreenScaffold(title = "", onBack = null, showTitle = false, contentPadding = PaddingValues(0.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy((layout.railSpacing * 0.8f).coerceAtLeast(14.dp)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val eyebrow = listOf(movie.year, movie.area).filter { it.isNotBlank() }.joinToString(" · ")
                if (eyebrow.isNotBlank()) {
                    Text(
                        text = eyebrow,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.headlineLarge,
                )
                val subtitle = listOf(
                    movie.remarks,
                    movie.actor.takeIf { it.isNotBlank() }?.let { "主演：$it" },
                ).filterNotNull().joinToString("  ")
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(layout.railSpacing),
                verticalAlignment = Alignment.Top,
            ) {
                TvPosterCard(
                    item = movie.toPosterItem(),
                    width = layout.posterWidth + 16.dp,
                    modifier = Modifier.width(layout.posterWidth + 16.dp),
                    onClick = {},
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.28f),
                                        Color.Transparent,
                                    ),
                                ),
                            )
                            .padding(22.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = listOf(movie.director, movie.writer)
                                    .filter { it.isNotBlank() }
                                    .joinToString("   "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = movie.content.ifBlank { "暂无简介" },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = "当前播放 · 源 ${safeSourceIndex + 1}/${movie.sources.size.coerceAtLeast(1)} · 集 ${safeEpisodeIndex + 1}/${sourceEpisodes.size.coerceAtLeast(1)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            if (episodesLoading) {
                                Text(
                                    text = "正在同步最新选集列表...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    TvFocusChip(
                        text = if (playbackEpisode != null) "播放 ${playbackEpisode.name}" else "开始播放",
                        selected = playbackEpisode != null,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .focusRequester(playFocusRequester)
                            .focusProperties { down = sourceFocusRequester },
                        onClick = { coroutineScope.launch { playCurrentSelection() } },
                    )

                    Text("切换播放源", style = MaterialTheme.typography.headlineMedium)
                    if (movie.sources.isEmpty()) {
                        TvFeedbackPanel(
                            title = "暂无播放源",
                            message = "这部影片当前没有可用片源。",
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(maxOf(layout.sourceColumns, 8)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = layout.heroHeight * 0.38f)
                                .focusGroup(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(movie.sources, key = { index, source -> "${source.code}-$index" }) { index, source ->
                                TvFocusChip(
                                    text = source.name,
                                    selected = index == safeSourceIndex,
                                    modifier = if (index == 0) {
                                        Modifier
                                            .focusRequester(sourceFocusRequester)
                                            .focusProperties { down = episodeFocusRequester }
                                    } else {
                                        Modifier.focusProperties { down = episodeFocusRequester }
                                    },
                                    onClick = {
                                        selectedSourceIndex = index
                                        selectedEpisodeIndex = 0
                                        statusHost?.show("切换播放源", "正在切换到 ${source.name}")
                                        coroutineScope.launch {
                                            val cached = episodesBySource[source.code]
                                            if (cached == null) {
                                                episodesLoading = true
                                                val fetched = try {
                                                    repository.getEpisodes(movie.id, source.code)
                                                } catch (_: Throwable) {
                                                    statusHost?.show("片源加载失败", "该片源的集数列表获取失败，已尝试使用已有数据。", timeoutMs = 2800L)
                                                    emptyList()
                                                }
                                                episodesBySource[source.code] = if (fetched.isNotEmpty()) fetched else source.episodes
                                                episodesLoading = false
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("选集", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = if (currentEpisode != null) {
                        "当前选中：${currentEpisode.name}"
                    } else if (episodesLoading) {
                        "正在准备选集..."
                    } else {
                        "当前没有可播放集数"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (sourceEpisodes.isEmpty()) {
                    TvFeedbackPanel(
                        title = if (episodesLoading) "正在加载选集" else "暂无可播放选集",
                        message = if (episodesLoading) {
                            "正在同步当前播放源的最新集数列表。"
                        } else {
                            "可以切换其它片源后再试。"
                        },
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(maxOf(layout.episodeColumns, 8)),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(
                                min = layout.heroHeight * 0.55f,
                                max = layout.heroHeight * 1.18f,
                            )
                            .focusGroup(),
                    ) {
                        itemsIndexed(sourceEpisodes, key = { index, episode -> "${episode.name}-$index" }) { index, episode ->
                            TvFocusChip(
                                text = episode.name,
                                selected = index == safeEpisodeIndex,
                                modifier = if (index == 0) {
                                    Modifier
                                        .focusRequester(episodeFocusRequester)
                                        .focusProperties { up = sourceFocusRequester }
                                } else {
                                    Modifier.focusProperties { up = sourceFocusRequester }
                                },
                                onClick = {
                                    selectedEpisodeIndex = index
                                    Log.d(tag, "episode selected: source=${currentSource?.name.orEmpty()} episode=${episode.name}")
                                    statusHost?.show("切换选集", "正在播放 ${episode.name}")
                                    onPlay("${movie.title} ${episode.name}", movie.id, safeSourceIndex, index)
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

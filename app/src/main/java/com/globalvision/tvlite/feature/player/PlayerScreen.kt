package com.globalvision.tvlite.feature.player

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.globalvision.tvlite.core.model.TvEpisode
import com.globalvision.tvlite.core.model.TvPlaySource
import com.globalvision.tvlite.core.network.TvRepository
import com.globalvision.tvlite.core.player.TvPlayerController
import com.globalvision.tvlite.feature.common.LocalTvStatusHostState
import com.globalvision.tvlite.feature.common.TvFocusChip
import com.globalvision.tvlite.ui.theme.TvFocusBorder
import com.globalvision.tvlite.ui.theme.rememberTvLayoutMetrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val SOURCE_LOAD_TIMEOUT_MS = 20_000L

@UnstableApi
@Composable
fun PlayerScreen(
    repository: TvRepository,
    title: String,
    movieId: String,
    sourceIndex: Int,
    episodeIndex: Int,
    onBack: () -> Unit,
) {
    val layout = rememberTvLayoutMetrics()
    val statusHost = LocalTvStatusHostState.current
    val tag = "PlayerScreen"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { TvPlayerController(context) }
    val controlsScrollState = rememberScrollState()

    var showControls by remember { mutableStateOf(true) }
    var controlsTick by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryInProgress by remember { mutableStateOf(false) }
    var waitingForPlayableSource by remember { mutableStateOf(false) }
    var initialPlaybackTriggered by rememberSaveable(movieId) { mutableStateOf(false) }
    var playAttemptToken by remember { mutableIntStateOf(0) }
    var sources by remember(movieId) { mutableStateOf<List<TvPlaySource>>(emptyList()) }
    val episodeCache = remember(movieId) { mutableStateMapOf<String, List<TvEpisode>>() }
    var currentSourceIndex by rememberSaveable(movieId) {
        mutableIntStateOf(sourceIndex.coerceAtLeast(0))
    }
    var currentEpisodeIndex by rememberSaveable(movieId) {
        mutableIntStateOf(episodeIndex.coerceAtLeast(0))
    }

    val playPauseFocusRequester = remember { FocusRequester() }
    val sourceFocusRequester = remember { FocusRequester() }
    val episodeFocusRequester = remember { FocusRequester() }
    val overlaySpacing = (layout.railSpacing * 0.6f).coerceAtLeast(12.dp)
    val overlayPaddingH = (layout.railSpacing * 1.1f).coerceAtLeast(18.dp)
    val overlayPaddingV = (layout.railSpacing * 0.9f).coerceAtLeast(16.dp)

    fun flashControls() {
        showControls = true
        controlsTick += 1
    }

    fun beginPlayback(playUrl: String, loadingText: String = "正在加载视频...") {
        errorMessage = null
        loadingMessage = loadingText
        waitingForPlayableSource = true
        playAttemptToken += 1
        controller.play(playUrl)
        flashControls()
    }

    LaunchedEffect(movieId) {
        Log.d(
            tag,
            "enter player: title=$title movieId=$movieId sourceIndex=$sourceIndex episodeIndex=$episodeIndex",
        )
        if (movieId.isBlank()) {
            errorMessage = "当前没有可播放地址，请返回后重新选择。"
            return@LaunchedEffect
        }
        initialPlaybackTriggered = false
        loadingMessage = "正在准备可播放来源..."
        val detail = try {
            repository.getDetail(movieId)
        } catch (throwable: Throwable) {
            Log.w(tag, "load detail for player failed: movieId=$movieId", throwable)
            null
        }
        sources = detail?.sources.orEmpty()
        if (sources.isNotEmpty()) {
            currentSourceIndex = sourceIndex.coerceIn(0, sources.lastIndex)
            currentEpisodeIndex = episodeIndex.coerceAtLeast(0)
            statusHost?.show("播放器已准备", "可直接播放，也可在下方切换片源和选集。")
        } else {
            errorMessage = "当前视频暂无可播放来源，请返回重新选择。"
        }
    }

    suspend fun loadEpisodesForSource(source: TvPlaySource): List<TvEpisode> {
        episodeCache[source.code]?.let { return it }
        val fetched = try {
            repository.getEpisodes(movieId, source.code)
        } catch (throwable: Throwable) {
            Log.w(tag, "load episodes for player failed: movieId=$movieId source=${source.code}", throwable)
            source.episodes
        }
        episodeCache[source.code] = fetched
        return fetched
    }

    suspend fun playSourceEpisode(targetSourceIndex: Int, targetEpisodeIndex: Int): Boolean {
        val source = sources.getOrNull(targetSourceIndex) ?: return false
        val episodes = loadEpisodesForSource(source)
        if (episodes.isEmpty()) return false
        val safeEpisode = targetEpisodeIndex.coerceIn(0, episodes.lastIndex)
        val episode = episodes.getOrNull(safeEpisode) ?: return false
        val playUrl = try {
            repository.resolveEpisodeUrl(episode)
        } catch (throwable: Throwable) {
            Log.w(tag, "resolve episode failed: movieId=$movieId source=${source.code} episode=${episode.name}", throwable)
            ""
        }
        if (playUrl.isBlank()) return false

        currentSourceIndex = targetSourceIndex
        currentEpisodeIndex = safeEpisode
        statusHost?.show("开始播放", "正在切换到 ${source.name} · ${episode.name}")
        beginPlayback(playUrl)
        return true
    }

    suspend fun playAdjacentEpisode(offset: Int): Boolean {
        val source = sources.getOrNull(currentSourceIndex) ?: return false
        val episodes = loadEpisodesForSource(source)
        if (episodes.isEmpty()) return false
        val nextIndex = (currentEpisodeIndex + offset).coerceIn(0, episodes.lastIndex)
        if (nextIndex == currentEpisodeIndex) return false
        return playSourceEpisode(currentSourceIndex, nextIndex)
    }

    suspend fun tryNextSource(): Boolean {
        if (movieId.isBlank() || sources.isEmpty()) return false
        for (offset in 1 until sources.size) {
            val index = (currentSourceIndex + offset) % sources.size
            loadingMessage = "当前来源暂时不可用，正在尝试其他来源..."
            if (playSourceEpisode(index, currentEpisodeIndex)) {
                return true
            }
        }
        return false
    }

    LaunchedEffect(movieId, sources) {
        if (movieId.isBlank() || sources.isEmpty() || initialPlaybackTriggered) return@LaunchedEffect
        initialPlaybackTriggered = true
        val started = playSourceEpisode(currentSourceIndex, currentEpisodeIndex)
        if (!started) {
            val switched = tryNextSource()
            if (!switched) {
                waitingForPlayableSource = false
                loadingMessage = null
                errorMessage = "这个视频暂时无法播放，请稍后再试其它片源。"
            }
        }
    }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                when (playbackState) {
                    Player.STATE_IDLE -> {
                        waitingForPlayableSource = false
                    }
                    Player.STATE_READY, Player.STATE_ENDED -> {
                        waitingForPlayableSource = false
                        if (loadingMessage != null) {
                            loadingMessage = null
                        }
                        errorMessage = null
                    }
                    Player.STATE_BUFFERING -> {
                        if (loadingMessage.isNullOrBlank()) {
                            loadingMessage = "正在加载视频..."
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(tag, "playback error: ${error.errorCodeName}", error)
                if (retryInProgress) return
                retryInProgress = true
                waitingForPlayableSource = false
                scope.launch {
                    val switched = tryNextSource()
                    retryInProgress = false
                    if (!switched) {
                        loadingMessage = null
                        errorMessage = "这个视频暂时无法播放，请稍后再试其它片源。"
                        statusHost?.show("播放失败", "当前所有可尝试片源均不可用。", timeoutMs = 2800L)
                    }
                }
            }
        }
        controller.addListener(listener)
        onDispose {
            controller.removeListener(listener)
            controller.release()
        }
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            playPauseFocusRequester.requestFocus()
        }
    }

    BackHandler {
        if (showControls) {
            onBack()
        } else {
            flashControls()
        }
    }

    LaunchedEffect(controlsTick) {
        if (showControls) {
            delay(3500)
            showControls = false
        }
    }

    LaunchedEffect(playAttemptToken) {
        if (playAttemptToken == 0 || !waitingForPlayableSource) return@LaunchedEffect
        val attemptToken = playAttemptToken
        delay(SOURCE_LOAD_TIMEOUT_MS)
        if (!waitingForPlayableSource || playAttemptToken != attemptToken || retryInProgress) {
            return@LaunchedEffect
        }

        retryInProgress = true
        loadingMessage = "当前来源加载较慢，正在尝试其他来源..."
        val switched = tryNextSource()
        retryInProgress = false
        if (!switched && playAttemptToken == attemptToken) {
            waitingForPlayableSource = false
            loadingMessage = null
            errorMessage = "这个视频暂时无法播放，请稍后再试其它片源。"
            statusHost?.show("播放失败", "尝试切换片源后仍未成功。", timeoutMs = 2800L)
        }
    }

    LaunchedEffect(movieId, showControls) {
        while (isActive) {
            positionMs = controller.currentPositionMs()
            durationMs = controller.durationMs()
            isPlaying = controller.isPlaying()
            delay(if (showControls) 250L else 900L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        controller.seekBy(-10_000)
                        flashControls()
                        true
                    }
                    Key.DirectionRight -> {
                        controller.seekBy(10_000)
                        flashControls()
                        true
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        flashControls()
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause, Key.Spacebar -> {
                        controller.togglePlayPause()
                        flashControls()
                        true
                    }
                    Key.MediaNext -> {
                        scope.launch {
                            val switched = playAdjacentEpisode(1)
                            if (!switched) {
                                statusHost?.show("已经是最后一集", "当前没有更后的可播放集数。")
                            }
                        }
                        true
                    }
                    Key.MediaPrevious -> {
                        scope.launch {
                            val switched = playAdjacentEpisode(-1)
                            if (!switched) {
                                statusHost?.show("已经是第一集", "当前没有更早的可播放集数。")
                            }
                        }
                        true
                    }
                    Key.Back -> {
                        if (showControls) {
                            onBack()
                        } else {
                            flashControls()
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = controller.player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (loadingMessage != null || isBuffering) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(layout.railSpacing + 4.dp),
                shape = RoundedCornerShape((layout.railSpacing * 1.2f).coerceAtLeast(22.dp)),
                color = Color(0xCC101010),
                tonalElevation = 4.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = overlayPaddingH, vertical = overlayPaddingV),
                    horizontalArrangement = Arrangement.spacedBy((layout.railSpacing * 0.7f).coerceAtLeast(14.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size((layout.railSpacing * 1.2f).coerceIn(22.dp, 28.dp)),
                        strokeWidth = 2.5.dp,
                        color = Color.White,
                    )
                    Text(
                        text = loadingMessage ?: "正在加载视频...",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }
        }

        if (errorMessage != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(layout.railSpacing + 4.dp),
                shape = RoundedCornerShape((layout.railSpacing * 1.2f).coerceAtLeast(22.dp)),
                color = Color(0xDD111111),
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = overlayPaddingH + 2.dp, vertical = overlayPaddingV + 2.dp),
                    verticalArrangement = Arrangement.spacedBy((layout.railSpacing * 0.7f).coerceAtLeast(14.dp)),
                ) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Text(
                        text = "你可以返回重新选择其它来源，或稍后再试。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.82f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(overlaySpacing)) {
                        PlayerControlButton(
                            text = "返回",
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                        )
                        PlayerControlButton(
                            text = "重试",
                            onClick = {
                                errorMessage = null
                                scope.launch {
                                    loadingMessage = "正在重新尝试播放..."
                                    val started = playSourceEpisode(currentSourceIndex, currentEpisodeIndex)
                                    if (!started) {
                                        val switched = tryNextSource()
                                        if (!switched) {
                                            waitingForPlayableSource = false
                                            loadingMessage = null
                                            errorMessage = "这个视频暂时无法播放，请稍后再试其它片源。"
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (showControls) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = layout.horizontalPadding / 2, vertical = layout.railSpacing + 2.dp)
                    .focusable(),
                shape = RoundedCornerShape((layout.railSpacing * 1.4f).coerceAtLeast(28.dp)),
                color = Color(0xF20B0C10),
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = layout.heroHeight * 1.7f)
                        .verticalScroll(controlsScrollState)
                        .padding(horizontal = overlayPaddingH, vertical = overlayPaddingV),
                    verticalArrangement = Arrangement.spacedBy(overlaySpacing),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(overlaySpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title.ifBlank { "视频播放" },
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (isBuffering) "加载中" else if (isPlaying) "播放中" else "已暂停",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.82f),
                        )
                    }

                    Text(
                        text = buildPlaybackLabel(positionMs, durationMs),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.82f),
                    )

                    LinearProgressIndicator(
                        progress = {
                            if (durationMs > 0) {
                                (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.12f),
                    )

                    Text(
                        text = "左右键快进后退，确认键播放暂停，返回键先呼出控制层，再次返回退出",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.88f),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(overlaySpacing),
                    ) {
                        PlayerControlButton(
                            text = "返回",
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                        )
                        PlayerControlButton(
                            text = "上一集",
                            onClick = {
                                scope.launch {
                                    val switched = playAdjacentEpisode(-1)
                                    if (!switched) {
                                        statusHost?.show("已经是第一集", "当前没有更早的可播放集数。")
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        PlayerControlButton(
                            text = if (isPlaying) "暂停" else "播放",
                            onClick = {
                                controller.togglePlayPause()
                                statusHost?.show(
                                    if (isPlaying) "已暂停播放" else "继续播放",
                                    if (isPlaying) "已暂停当前视频。" else "正在继续当前视频。",
                                )
                                flashControls()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(playPauseFocusRequester),
                            selected = isPlaying,
                        )
                        PlayerControlButton(
                            text = "下一集",
                            onClick = {
                                scope.launch {
                                    val switched = playAdjacentEpisode(1)
                                    if (!switched) {
                                        statusHost?.show("已经是最后一集", "当前没有更后的可播放集数。")
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        PlayerControlButton(
                            text = "后退30秒",
                            onClick = {
                                controller.seekBy(-30_000)
                                statusHost?.show("已后退 30 秒", buildPlaybackLabel(controller.currentPositionMs(), controller.durationMs()))
                                flashControls()
                            },
                            modifier = Modifier.weight(1f),
                        )
                        PlayerControlButton(
                            text = "前进30秒",
                            onClick = {
                                controller.seekBy(30_000)
                                statusHost?.show("已前进 30 秒", buildPlaybackLabel(controller.currentPositionMs(), controller.durationMs()))
                                flashControls()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    val currentSource = sources.getOrNull(currentSourceIndex)
                    val currentEpisodes = currentSource?.code?.let { episodeCache[it] }.orEmpty()
                        .ifEmpty { currentSource?.episodes.orEmpty() }

                    if (sources.isNotEmpty()) {
                        Text(
                            text = "播放源切换",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.84f),
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(maxOf(layout.sourceColumns, 8)),
                            contentPadding = PaddingValues(0.dp),
                            horizontalArrangement = Arrangement.spacedBy((layout.railSpacing * 0.5f).coerceAtLeast(10.dp)),
                            verticalArrangement = Arrangement.spacedBy((layout.railSpacing * 0.5f).coerceAtLeast(10.dp)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = layout.heroHeight * 0.3f)
                                .focusGroup(),
                        ) {
                            itemsIndexed(sources, key = { index, source -> "${source.code}-$index" }) { index, source ->
                                TvFocusChip(
                                    text = source.name,
                                    selected = index == currentSourceIndex,
                                    modifier = if (index == 0) {
                                        Modifier.focusRequester(sourceFocusRequester)
                                    } else {
                                        Modifier
                                    },
                                    onClick = {
                                        scope.launch {
                                            playSourceEpisode(index, 0)
                                        }
                                    },
                                )
                            }
                        }
                    }

                    if (currentEpisodes.isNotEmpty()) {
                        Text(
                            text = "选集切换",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.84f),
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(maxOf(layout.episodeColumns, 8)),
                            contentPadding = PaddingValues(0.dp),
                            horizontalArrangement = Arrangement.spacedBy((layout.railSpacing * 0.5f).coerceAtLeast(10.dp)),
                            verticalArrangement = Arrangement.spacedBy((layout.railSpacing * 0.5f).coerceAtLeast(10.dp)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = layout.heroHeight * 0.58f)
                                .focusGroup(),
                        ) {
                            itemsIndexed(currentEpisodes, key = { index, episode -> "${episode.name}-$index" }) { index, episode ->
                                TvFocusChip(
                                    text = episode.name,
                                    selected = index == currentEpisodeIndex,
                                    modifier = if (index == 0) {
                                        Modifier.focusRequester(episodeFocusRequester)
                                    } else {
                                        Modifier
                                    },
                                    onClick = {
                                        scope.launch {
                                            playSourceEpisode(currentSourceIndex, index)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerControlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val layout = rememberTvLayoutMetrics()
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val containerColor = when {
        selected || focused -> MaterialTheme.colorScheme.primary
        else -> Color(0xFF2B2B2B)
    }
    val borderColor = when {
        focused -> TvFocusBorder
        selected -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    val textColor = when {
        selected || focused -> MaterialTheme.colorScheme.onPrimary
        else -> Color.White
    }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
        label = "player_button_scale",
    )
    val elevation by animateFloatAsState(
        targetValue = if (focused) 18f else 0f,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 420f),
        label = "player_button_elevation",
    )

    Surface(
        onClick = onClick,
        shape = shape,
        color = containerColor,
        contentColor = textColor,
        tonalElevation = if (focused) 4.dp else 0.dp,
        modifier = modifier
            .height((layout.posterWidth * 0.22f).coerceIn(48.dp, 62.dp))
            .border(2.dp, borderColor, shape)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = elevation
            },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun buildPlaybackLabel(positionMs: Long, durationMs: Long): String {
    if (durationMs <= 0L) return formatTime(positionMs)
    return "${formatTime(positionMs)} / ${formatTime(durationMs)}"
}

private fun formatTime(valueMs: Long): String {
    val totalSeconds = (valueMs / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

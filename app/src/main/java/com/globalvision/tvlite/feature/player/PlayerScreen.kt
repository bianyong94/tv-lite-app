package com.globalvision.tvlite.feature.player

import android.util.Log
import android.os.SystemClock
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.globalvision.tvlite.core.model.TvEpisode
import com.globalvision.tvlite.core.model.TvPlaySource
import com.globalvision.tvlite.core.network.TvRepository
import com.globalvision.tvlite.core.player.TvPlayerController
import com.globalvision.tvlite.feature.common.consumeRepeatedDpadEvents
import com.globalvision.tvlite.feature.common.isRepeatedDpadEvent
import com.globalvision.tvlite.feature.common.LocalTvStatusHostState
import com.globalvision.tvlite.feature.common.TvFocusChip
import com.globalvision.tvlite.feature.common.ensureVisibleOnFocus
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
    val configuration = LocalConfiguration.current
    val controlPanelHeight = (configuration.screenHeightDp.dp * 0.2f).coerceAtLeast(180.dp)

    var showControls by remember { mutableStateOf(true) }
    var showEpisodePicker by remember { mutableStateOf(false) }
    var overlayLastTouchedAtMs by remember { mutableStateOf(0L) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var seekFeedbackText by remember { mutableStateOf<String?>(null) }
    var seekFeedbackToken by remember { mutableIntStateOf(0) }
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
    val overlaySpacing = (layout.railSpacing * 0.45f).coerceAtLeast(8.dp)
    val overlayPaddingH = (layout.railSpacing * 0.8f).coerceAtLeast(12.dp)
    val overlayPaddingV = (layout.railSpacing * 0.55f).coerceAtLeast(10.dp)

    val anyOverlayVisible = showControls || showEpisodePicker

    fun touchOverlay() {
        overlayLastTouchedAtMs = SystemClock.uptimeMillis()
    }

    fun showControlsOverlay() {
        showControls = true
        showEpisodePicker = false
        touchOverlay()
    }

    fun showEpisodePickerOverlay() {
        showEpisodePicker = true
        showControls = false
        touchOverlay()
    }

    fun hideAllOverlays() {
        showControls = false
        showEpisodePicker = false
    }

    fun keepOverlayAlive() {
        if (anyOverlayVisible) {
            touchOverlay()
        }
    }

    fun showSeekFeedback(prefix: String) {
        seekFeedbackText = "$prefix  ${buildPlaybackLabel(controller.currentPositionMs(), controller.durationMs())}"
        seekFeedbackToken += 1
    }

    fun beginPlayback(playUrl: String, loadingText: String = "正在加载视频...") {
        errorMessage = null
        loadingMessage = loadingText
        waitingForPlayableSource = true
        playAttemptToken += 1
        controller.play(playUrl)
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
            statusHost?.show("播放器已准备", "可直接播放，下键切换片源，菜单键切换集数。")
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

    LaunchedEffect(movieId, sources) {
        if (movieId.isBlank() || sources.isEmpty() || initialPlaybackTriggered) return@LaunchedEffect
        initialPlaybackTriggered = true
        val started = playSourceEpisode(currentSourceIndex, currentEpisodeIndex)
        if (!started) {
            waitingForPlayableSource = false
            loadingMessage = null
            errorMessage = "当前片源暂时无法播放，请手动切换其它片源。"
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
                waitingForPlayableSource = false
                loadingMessage = null
                errorMessage = "当前片源播放失败，请手动切换其它片源。"
                statusHost?.show("播放失败", "当前片源不可用，请手动切换播放源。", timeoutMs = 2800L)
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
            delay(32)
            playPauseFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(showEpisodePicker) {
        if (showEpisodePicker) {
            delay(32)
            episodeFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(showEpisodePicker, currentSourceIndex) {
        if (!showEpisodePicker) return@LaunchedEffect
        val source = sources.getOrNull(currentSourceIndex) ?: return@LaunchedEffect
        loadEpisodesForSource(source)
    }

    BackHandler(enabled = anyOverlayVisible) {
        hideAllOverlays()
    }

    BackHandler(enabled = !anyOverlayVisible) {
        onBack()
    }

    LaunchedEffect(anyOverlayVisible, overlayLastTouchedAtMs) {
        if (!anyOverlayVisible) return@LaunchedEffect
        val touchedAtMs = overlayLastTouchedAtMs
        delay(3500)
        if (anyOverlayVisible && overlayLastTouchedAtMs == touchedAtMs) {
            hideAllOverlays()
        }
    }

    LaunchedEffect(seekFeedbackToken) {
        if (seekFeedbackToken == 0) return@LaunchedEffect
        delay(1200)
        seekFeedbackText = null
    }

    LaunchedEffect(playAttemptToken) {
        if (playAttemptToken == 0 || !waitingForPlayableSource) return@LaunchedEffect
        val attemptToken = playAttemptToken
        delay(SOURCE_LOAD_TIMEOUT_MS)
        if (!waitingForPlayableSource || playAttemptToken != attemptToken) {
            return@LaunchedEffect
        }

        if (playAttemptToken == attemptToken) {
            waitingForPlayableSource = false
            loadingMessage = null
            errorMessage = "当前片源加载超时，请手动切换其它片源。"
            statusHost?.show("播放超时", "没有自动切源，请手动选择其它播放源。", timeoutMs = 2800L)
        }
    }

    LaunchedEffect(movieId, anyOverlayVisible) {
        while (isActive) {
            positionMs = controller.currentPositionMs()
            durationMs = controller.durationMs()
            isPlaying = controller.isPlaying()
            delay(if (anyOverlayVisible) 250L else 900L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.isRepeatedDpadEvent()) return@onPreviewKeyEvent true
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (showEpisodePicker) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.Back -> {
                            hideAllOverlays()
                            true
                        }
                        Key.DirectionDown -> {
                            showControlsOverlay()
                            true
                        }
                        Key.Menu -> {
                            keepOverlayAlive()
                            true
                        }
                        else -> false
                    }
                }
                if (showControls) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.Back -> {
                            hideAllOverlays()
                            true
                        }
                        Key.Menu -> {
                            showEpisodePickerOverlay()
                            true
                        }
                        else -> false
                    }
                }
                when (event.key) {
                    Key.DirectionLeft -> {
                        controller.seekBy(-10_000)
                        showSeekFeedback("后退 10 秒")
                        true
                    }
                    Key.DirectionRight -> {
                        controller.seekBy(10_000)
                        showSeekFeedback("前进 10 秒")
                        true
                    }
                    Key.DirectionDown -> {
                        showControlsOverlay()
                        true
                    }
                    Key.Menu -> {
                        showEpisodePickerOverlay()
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause, Key.Spacebar -> {
                        controller.togglePlayPause()
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
                        onBack()
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

        if (seekFeedbackText != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = layout.horizontalPadding / 2, vertical = layout.railSpacing + 12.dp),
                shape = RoundedCornerShape((layout.railSpacing * 1.2f).coerceAtLeast(22.dp)),
                color = Color(0xE60B0C10),
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .width((layout.posterWidth * 2.8f).coerceIn(420.dp, 640.dp))
                        .padding(horizontal = overlayPaddingH, vertical = overlayPaddingV),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = seekFeedbackText.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    LinearProgressIndicator(
                        progress = {
                            if (durationMs > 0) {
                                (controller.currentPositionMs().toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
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
                }
            }
        }

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
                            onFocused = ::keepOverlayAlive,
                            modifier = Modifier.widthIn(min = 110.dp),
                        )
                        PlayerControlButton(
                            text = "重试",
                            onClick = {
                                errorMessage = null
                                scope.launch {
                                    loadingMessage = "正在重新尝试播放..."
                                    val started = playSourceEpisode(currentSourceIndex, currentEpisodeIndex)
                                    if (!started) {
                                        waitingForPlayableSource = false
                                        loadingMessage = null
                                        errorMessage = "当前片源暂时无法播放，请手动切换其它片源。"
                                    }
                                }
                            },
                            onFocused = ::keepOverlayAlive,
                            modifier = Modifier.widthIn(min = 110.dp),
                        )
                    }
                }
            }
        }

        if (showControls) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = layout.horizontalPadding / 2, vertical = 16.dp)
                    .fillMaxWidth()
                    .height(controlPanelHeight),
                shape = RoundedCornerShape((layout.railSpacing * 1.4f).coerceAtLeast(28.dp)),
                color = Color(0xF20B0C10),
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
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
                        text = "下键呼出播放控制，菜单键切换集数，左右键在画面层快进快退，确认键播放暂停",
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
                            onFocused = ::keepOverlayAlive,
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
                                keepOverlayAlive()
                            },
                            onFocused = ::keepOverlayAlive,
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
                            onFocused = ::keepOverlayAlive,
                            modifier = Modifier.weight(1f),
                        )
                        PlayerControlButton(
                            text = "后退30秒",
                            onClick = {
                                controller.seekBy(-30_000)
                                statusHost?.show("已后退 30 秒", buildPlaybackLabel(controller.currentPositionMs(), controller.durationMs()))
                                showSeekFeedback("后退 30 秒")
                                keepOverlayAlive()
                            },
                            onFocused = ::keepOverlayAlive,
                            modifier = Modifier.weight(1f),
                        )
                        PlayerControlButton(
                            text = "前进30秒",
                            onClick = {
                                controller.seekBy(30_000)
                                statusHost?.show("已前进 30 秒", buildPlaybackLabel(controller.currentPositionMs(), controller.durationMs()))
                                showSeekFeedback("前进 30 秒")
                                keepOverlayAlive()
                            },
                            onFocused = ::keepOverlayAlive,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (sources.isNotEmpty()) {
                        Text(
                            text = "播放源切换",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.84f),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy((layout.railSpacing * 0.5f).coerceAtLeast(10.dp)),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp, max = 72.dp)
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
                                    }.onFocusChanged { if (it.isFocused) keepOverlayAlive() },
                                    onClick = {
                                        keepOverlayAlive()
                                        scope.launch {
                                            playSourceEpisode(index, 0)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showEpisodePicker) {
            val currentSource = sources.getOrNull(currentSourceIndex)
            val currentEpisodes = currentSource?.code?.let { episodeCache[it] }.orEmpty()
                .ifEmpty { currentSource?.episodes.orEmpty() }
            val episodePanelHeight = (configuration.screenHeightDp.dp * 0.35f).coerceAtLeast(200.dp)

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = layout.horizontalPadding / 2, vertical = 16.dp)
                    .fillMaxWidth()
                    .height(episodePanelHeight),
                shape = RoundedCornerShape((layout.railSpacing * 1.4f).coerceAtLeast(28.dp)),
                color = Color(0xF20B0C10),
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = overlayPaddingH, vertical = overlayPaddingV),
                    verticalArrangement = Arrangement.spacedBy(overlaySpacing),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "选集切换",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                        )
                        Text(
                            text = currentSource?.name.orEmpty().ifBlank { "当前片源" },
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.82f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Text(
                        text = "菜单键呼出选集，下键切换到播放控制，返回键关闭弹框",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.88f),
                    )

                    if (currentEpisodes.isEmpty()) {
                        Text(
                            text = "正在加载集数...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.82f),
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(maxOf(layout.episodeColumns, 8)),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy((layout.railSpacing * 0.5f).coerceAtLeast(10.dp)),
                            verticalArrangement = Arrangement.spacedBy((layout.railSpacing * 0.5f).coerceAtLeast(10.dp)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .focusGroup(),
                        ) {
                            itemsIndexed(currentEpisodes, key = { index, episode -> "${episode.name}-$index" }) { index, episode ->
                                TvFocusChip(
                                    text = episode.name,
                                    selected = index == currentEpisodeIndex,
                                    modifier = if (index == currentEpisodeIndex) {
                                        Modifier.focusRequester(episodeFocusRequester)
                                    } else {
                                        Modifier
                                    }.onFocusChanged { if (it.isFocused) keepOverlayAlive() },
                                    onClick = {
                                        keepOverlayAlive()
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
    onFocused: (() -> Unit)? = null,
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
            .consumeRepeatedDpadEvents()
            .ensureVisibleOnFocus()
            .height((layout.posterWidth * 0.22f).coerceIn(48.dp, 62.dp))
            .border(if (focused) 2.5.dp else if (selected) 2.dp else 0.dp, borderColor, shape)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onFocused?.invoke()
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    false
                } else {
                    event.isRepeatedDpadEvent()
                }
            }
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

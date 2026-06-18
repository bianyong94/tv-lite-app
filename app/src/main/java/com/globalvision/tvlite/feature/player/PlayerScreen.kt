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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.globalvision.tvlite.core.local.TvPlaybackHistoryStore
import com.globalvision.tvlite.core.model.TvEpisode
import com.globalvision.tvlite.core.model.TvMovieDetail
import com.globalvision.tvlite.core.model.TvPlaybackHistoryEntry
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

private enum class PlayerOverlayPanel {
    Controls,
    Menu,
}

@UnstableApi
@Composable
fun PlayerScreen(
    repository: TvRepository,
    title: String,
    movieId: String,
    sourceIndex: Int,
    episodeIndex: Int,
    initialPositionMs: Long = 0L,
    onBack: () -> Unit,
) {
    val layout = rememberTvLayoutMetrics()
    val statusHost = LocalTvStatusHostState.current
    val tag = "PlayerScreen"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { TvPlayerController(context) }
    val historyStore = remember(context) { TvPlaybackHistoryStore(context) }

    var overlayPanel by remember { mutableStateOf<PlayerOverlayPanel?>(PlayerOverlayPanel.Controls) }
    var overlayLastTouchedAtMs by remember { mutableStateOf(0L) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(controller.playbackSpeed()) }
    var loadingMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var seekFeedbackText by remember { mutableStateOf<String?>(null) }
    var seekFeedbackToken by remember { mutableIntStateOf(0) }
    var waitingForPlayableSource by remember { mutableStateOf(false) }
    var initialPlaybackTriggered by rememberSaveable(movieId) { mutableStateOf(false) }
    var playAttemptToken by remember { mutableIntStateOf(0) }
    var playbackLoadStartedAtMs by remember { mutableStateOf(0L) }
    var readyLoggedForAttempt by remember { mutableIntStateOf(0) }
    var seekStartedAtMs by remember { mutableStateOf(0L) }
    var currentPlaybackEpisode by remember { mutableStateOf<TvEpisode?>(null) }
    var parseFallbackAllowed by remember { mutableStateOf(false) }
    var parseFallbackAttempted by remember { mutableStateOf(false) }
    var detail by remember(movieId) { mutableStateOf<TvMovieDetail?>(null) }
    var sources by remember(movieId) { mutableStateOf<List<TvPlaySource>>(emptyList()) }
    val episodeCache = remember(movieId) { mutableStateMapOf<String, List<TvEpisode>>() }
    var pendingSeekPositionMs by rememberSaveable(movieId) { mutableStateOf(0L) }
    var shouldApplyPendingSeek by rememberSaveable(movieId) { mutableStateOf(false) }
    var currentSourceIndex by rememberSaveable(movieId) {
        mutableIntStateOf(sourceIndex.coerceAtLeast(0))
    }
    var currentEpisodeIndex by rememberSaveable(movieId) {
        mutableIntStateOf(episodeIndex.coerceAtLeast(0))
    }

    val playPauseFocusRequester = remember { FocusRequester() }
    val errorBackFocusRequester = remember { FocusRequester() }
    val errorRetryFocusRequester = remember { FocusRequester() }
    
    // Apple TV 风格的间距设计
    val overlaySpacing = 16.dp
    val contentPaddingH = 56.dp
    val contentPaddingBottom = 48.dp

    val showControls = overlayPanel == PlayerOverlayPanel.Controls
    val showEpisodePicker = overlayPanel == PlayerOverlayPanel.Menu
    val anyOverlayVisible = overlayPanel != null
    val shouldTrackPlaybackUi = anyOverlayVisible || seekFeedbackText != null
    val pickerSource = sources.getOrNull(currentSourceIndex)
    val pickerEpisodes = pickerSource?.code?.let { episodeCache[it] }.orEmpty()
        .ifEmpty { pickerSource?.episodes.orEmpty() }
    val sourceChipRequesters = remember(sources.size) {
        List(sources.size) { FocusRequester() }
    }
    val episodeChipRequesters = remember(pickerSource?.code, pickerEpisodes.size) {
        List(pickerEpisodes.size) { FocusRequester() }
    }

    fun touchOverlay() {
        overlayLastTouchedAtMs = SystemClock.uptimeMillis()
    }

    fun showControlsOverlay() {
        overlayPanel = PlayerOverlayPanel.Controls
        touchOverlay()
    }

    fun showEpisodePickerOverlay() {
        overlayPanel = PlayerOverlayPanel.Menu
        touchOverlay()
    }

    fun hideAllOverlays() {
        overlayPanel = null
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

    fun applyPlaybackSpeed(speed: Float) {
        controller.setPlaybackSpeed(speed)
        playbackSpeed = controller.playbackSpeed()
    }

    fun beginPlayback(
        playUrl: String,
        loadingText: String = "正在加载...",
        episode: TvEpisode? = null,
        allowParseFallback: Boolean = true,
    ) {
        errorMessage = null
        loadingMessage = loadingText
        waitingForPlayableSource = true
        playbackLoadStartedAtMs = SystemClock.uptimeMillis()
        currentPlaybackEpisode = episode
        parseFallbackAllowed = allowParseFallback
        parseFallbackAttempted = false
        playAttemptToken += 1
        readyLoggedForAttempt = 0
        seekStartedAtMs = 0L
        Log.d(tag, "player prepare start: episode=${episode?.name.orEmpty()} allowParseFallback=$allowParseFallback url=${playUrl.take(96)}")
        controller.play(playUrl)
    }

    fun persistHistoryAndBack() {
        val movieDetail = detail
        val source = sources.getOrNull(currentSourceIndex)
        val episodes = source?.code?.let { episodeCache[it] }.orEmpty().ifEmpty { source?.episodes.orEmpty() }
        val episode = episodes.getOrNull(currentEpisodeIndex)
        if (movieId.isNotBlank() && movieDetail != null) {
            historyStore.upsert(
                TvPlaybackHistoryEntry(
                    movieId = movieDetail.id,
                    title = movieDetail.title,
                    posterUrl = movieDetail.posterUrl,
                    year = movieDetail.year,
                    category = movieDetail.area,
                    remark = movieDetail.remarks,
                    sourceIndex = currentSourceIndex,
                    sourceName = source?.name.orEmpty(),
                    episodeIndex = currentEpisodeIndex,
                    episodeName = episode?.name.orEmpty(),
                    positionMs = controller.currentPositionMs(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        onBack()
    }

    LaunchedEffect(movieId) {
        Log.d(tag, "enter player: title=$title movieId=$movieId")
        if (movieId.isBlank()) {
            errorMessage = "当前没有可播放地址，请返回后重新选择。"
            return@LaunchedEffect
        }
        initialPlaybackTriggered = false
        loadingMessage = "正在准备媒体源..."
        val loadedDetail = try {
            repository.getDetail(movieId)
        } catch (throwable: Throwable) {
            null
        }
        detail = loadedDetail
        sources = loadedDetail?.sources.orEmpty()
        pendingSeekPositionMs = initialPositionMs.coerceAtLeast(0L)
        shouldApplyPendingSeek = pendingSeekPositionMs > 0L
        if (sources.isNotEmpty()) {
            currentSourceIndex = sourceIndex.coerceIn(0, sources.lastIndex)
            currentEpisodeIndex = episodeIndex.coerceAtLeast(0)
            statusHost?.show("播放器已就绪", "下键唤出控制台，菜单键快速选集。")
        } else {
            errorMessage = "当前视频暂无可播放来源，请返回重新选择。"
        }
    }

    suspend fun loadEpisodesForSource(source: TvPlaySource): List<TvEpisode> {
        episodeCache[source.code]?.let { return it }
        val fetched = try {
            repository.getEpisodes(movieId, source.code)
        } catch (throwable: Throwable) {
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
        val resolveStartedAtMs = SystemClock.uptimeMillis()
        val playUrl = try {
            repository.resolveEpisodeUrl(episode)
        } catch (throwable: Throwable) {
            ""
        }
        if (playUrl.isBlank()) return false
        Log.d(
            tag,
            "episode url resolved: source=${source.name} episode=${episode.name} elapsed=${SystemClock.uptimeMillis() - resolveStartedAtMs}ms url=${playUrl.take(96)}",
        )

        currentSourceIndex = targetSourceIndex
        currentEpisodeIndex = safeEpisode
        pendingSeekPositionMs = if (
            targetSourceIndex == sourceIndex &&
            safeEpisode == episodeIndex
        ) {
            initialPositionMs.coerceAtLeast(0L)
        } else {
            0L
        }
        shouldApplyPendingSeek = pendingSeekPositionMs > 0L
        statusHost?.show("开始播放", "正在呈现 ${source.name} · ${episode.name}")
        beginPlayback(playUrl, episode = episode)
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

    suspend fun playNextSource(): Boolean {
        if (sources.isEmpty()) return false
        val nextSourceIndex = currentSourceIndex + 1
        if (nextSourceIndex > sources.lastIndex) return false
        val nextSource = sources[nextSourceIndex]
        statusHost?.show("切换线路", "正在尝试 ${nextSource.name}")
        return playSourceEpisode(nextSourceIndex, currentEpisodeIndex)
    }

    LaunchedEffect(movieId, sources) {
        if (movieId.isBlank() || sources.isEmpty() || initialPlaybackTriggered) return@LaunchedEffect
        initialPlaybackTriggered = true
        val started = playSourceEpisode(currentSourceIndex, currentEpisodeIndex)
        if (!started) {
            waitingForPlayableSource = false
            loadingMessage = null
            errorMessage = "该片源暂时无法播放，请尝试切换其它线路。"
        }
    }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                durationMs = controller.durationMs()
                when (playbackState) {
                    Player.STATE_IDLE -> waitingForPlayableSource = false
                    Player.STATE_READY, Player.STATE_ENDED -> {
                        if (playbackState == Player.STATE_READY && shouldApplyPendingSeek && pendingSeekPositionMs > 0L) {
                            controller.seekTo(pendingSeekPositionMs)
                            shouldApplyPendingSeek = false
                        }
                        if (playbackState == Player.STATE_READY) {
                            if (readyLoggedForAttempt != playAttemptToken) {
                                readyLoggedForAttempt = playAttemptToken
                                val elapsedMs = (SystemClock.uptimeMillis() - playbackLoadStartedAtMs)
                                    .takeIf { playbackLoadStartedAtMs > 0L }
                                    ?: 0L
                                Log.d(
                                    tag,
                                    "player startup ready: elapsed=${elapsedMs}ms buffered=${controller.bufferedPositionMs()} duration=${controller.durationMs()}",
                                )
                                controller.allowAdaptiveVideoQuality()
                            } else if (seekStartedAtMs > 0L) {
                                val seekElapsedMs = SystemClock.uptimeMillis() - seekStartedAtMs
                                Log.d(
                                    tag,
                                    "player seek ready: elapsed=${seekElapsedMs}ms position=${controller.currentPositionMs()} buffered=${controller.bufferedPositionMs()}",
                                )
                                seekStartedAtMs = 0L
                            }
                        }
                        waitingForPlayableSource = false
                        loadingMessage = null
                        errorMessage = null
                    }
                    Player.STATE_BUFFERING -> {
                        if (loadingMessage.isNullOrBlank()) loadingMessage = "正在缓冲..."
                        Log.d(
                            tag,
                            "player buffering: position=${controller.currentPositionMs()} buffered=${controller.bufferedPositionMs()}",
                        )
                    }
                }
            }
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
            }
            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                playbackSpeed = playbackParameters.speed
            }
            override fun onPlayerError(error: PlaybackException) {
                val failedEpisode = currentPlaybackEpisode
                val canTryParseFallback = parseFallbackAllowed && !parseFallbackAttempted && failedEpisode != null
                Log.w(
                    tag,
                    "player error: code=${error.errorCode} canTryParseFallback=$canTryParseFallback message=${error.message}",
                    error,
                )
                if (canTryParseFallback) {
                    parseFallbackAttempted = true
                    loadingMessage = "正在尝试兼容播放..."
                    waitingForPlayableSource = true
                    scope.launch {
                        val fallbackStartedAtMs = SystemClock.uptimeMillis()
                        val fallbackUrl = try {
                            repository.resolveEpisodeUrlWithParseFallback(failedEpisode!!)
                        } catch (throwable: Throwable) {
                            Log.w(tag, "parse fallback resolve failed", throwable)
                            ""
                        }
                        Log.d(
                            tag,
                            "parse fallback resolved: elapsed=${SystemClock.uptimeMillis() - fallbackStartedAtMs}ms url=${fallbackUrl.take(96)}",
                        )
                        if (fallbackUrl.isNotBlank()) {
                            beginPlayback(
                                playUrl = fallbackUrl,
                                loadingText = "正在切换兼容播放...",
                                episode = failedEpisode,
                                allowParseFallback = false,
                            )
                        } else {
                            overlayPanel = null
                            waitingForPlayableSource = false
                            loadingMessage = null
                            errorMessage = "视频解码或网络异常，请尝试切换路线。"
                            statusHost?.show("播放受阻", "当前片源不可用，请手动切换播放源。", timeoutMs = 2800L)
                        }
                    }
                    return
                }
                overlayPanel = null
                waitingForPlayableSource = false
                loadingMessage = null
                errorMessage = "视频解码或网络异常，请尝试切换路线。"
                statusHost?.show("播放受阻", "当前片源不可用，请手动切换播放源。", timeoutMs = 2800L)
            }
        }
        controller.addListener(listener)
        onDispose {
            controller.removeListener(listener)
            controller.release()
        }
    }

    LaunchedEffect(showControls) {
        if (overlayPanel == PlayerOverlayPanel.Controls) {
            delay(32)
            playPauseFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(overlayPanel, currentSourceIndex, pickerEpisodes.size) {
        if (overlayPanel == PlayerOverlayPanel.Menu) {
            delay(32)
            when {
                pickerEpisodes.isNotEmpty() -> {
                    val target = currentEpisodeIndex.coerceIn(0, pickerEpisodes.lastIndex)
                    episodeChipRequesters[target].requestFocus()
                }
                sources.isNotEmpty() -> {
                    val target = currentSourceIndex.coerceIn(0, sources.lastIndex)
                    sourceChipRequesters[target].requestFocus()
                }
            }
        }
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            delay(32)
            errorRetryFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(overlayPanel, currentSourceIndex) {
        if (overlayPanel != PlayerOverlayPanel.Menu) return@LaunchedEffect
        val source = sources.getOrNull(currentSourceIndex) ?: return@LaunchedEffect
        loadEpisodesForSource(source)
    }

    BackHandler(enabled = anyOverlayVisible) { hideAllOverlays() }
    BackHandler(enabled = !anyOverlayVisible) { persistHistoryAndBack() }

    LaunchedEffect(anyOverlayVisible, overlayLastTouchedAtMs) {
        if (!anyOverlayVisible) return@LaunchedEffect
        val touchedAtMs = overlayLastTouchedAtMs
        delay(4000)
        if (anyOverlayVisible && overlayLastTouchedAtMs == touchedAtMs) {
            hideAllOverlays()
        }
    }

    LaunchedEffect(seekFeedbackToken) {
        if (seekFeedbackToken == 0) return@LaunchedEffect
        delay(1500)
        seekFeedbackText = null
    }

    LaunchedEffect(playAttemptToken) {
        if (playAttemptToken == 0 || !waitingForPlayableSource) return@LaunchedEffect
        val attemptToken = playAttemptToken
        delay(SOURCE_LOAD_TIMEOUT_MS)
        if (!waitingForPlayableSource || playAttemptToken != attemptToken) return@LaunchedEffect
        overlayPanel = null
        waitingForPlayableSource = false
        loadingMessage = null
        errorMessage = "连接资源超时，请手动切换其它片源。"
        statusHost?.show("加载超时", "未能成功连接到媒体库，请切换路线。", timeoutMs = 2800L)
    }

    LaunchedEffect(movieId, shouldTrackPlaybackUi) {
        positionMs = controller.currentPositionMs()
        durationMs = controller.durationMs()
        if (!shouldTrackPlaybackUi) return@LaunchedEffect
        while (isActive) {
            positionMs = controller.currentPositionMs()
            durationMs = controller.durationMs()
            delay(250L)
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

                if (errorMessage != null) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.Back -> {
                            persistHistoryAndBack()
                            true
                        }
                        Key.MediaNext,
                        Key.MediaPrevious,
                        Key.MediaPlayPause,
                        Key.Menu,
                        Key.DirectionLeft,
                        Key.DirectionRight -> false
                        else -> false
                    }
                }
                
                // 完全恢复原版按键拦截逻辑
                if (overlayPanel == PlayerOverlayPanel.Menu) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.Back -> {
                            hideAllOverlays()
                            true
                        }
                        else -> false
                    }
                }
                if (overlayPanel == PlayerOverlayPanel.Controls) {
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
                        seekStartedAtMs = SystemClock.uptimeMillis()
                        Log.d(tag, "player seek request: offset=-10000 position=${controller.currentPositionMs()}")
                        controller.seekBy(-10_000)
                        showSeekFeedback("后退 10 秒")
                        true
                    }
                    Key.DirectionRight -> {
                        seekStartedAtMs = SystemClock.uptimeMillis()
                        Log.d(tag, "player seek request: offset=10000 position=${controller.currentPositionMs()}")
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
                        persistHistoryAndBack()
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

        // Apple TV 风格顶部状态 / 反馈面板
        if (seekFeedbackText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
                    .background(Color(0x66000000), CircleShape)
                    .border(1.dp, Color(0x33FFFFFF), CircleShape)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = seekFeedbackText.orEmpty(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    ),
                    color = Color.White,
                )
            }
        }

        // 居中加载框（毛玻璃药丸风格）
        if (loadingMessage != null || isBuffering) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0x80000000), CircleShape)
                    .border(1.dp, Color(0x22FFFFFF), CircleShape)
                    .padding(horizontal = 32.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                    color = Color.White,
                )
                Text(
                    text = loadingMessage ?: "正在缓冲...",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = Color.White,
                )
            }
        }

        // 报错面板（毛玻璃卡片风格）
        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                PlayerErrorDialog(
                    errorMessage = errorMessage.orEmpty(),
                    errorBackFocusRequester = errorBackFocusRequester,
                    errorRetryFocusRequester = errorRetryFocusRequester,
                    onBack = ::persistHistoryAndBack,
                    onFocused = ::keepOverlayAlive,
                    onRetry = {
                        errorMessage = null
                        scope.launch {
                            loadingMessage = "正在切换下一个片源..."
                            val started = playNextSource()
                            if (!started) {
                                waitingForPlayableSource = false
                                loadingMessage = null
                                errorMessage = "没有更多可切换的片源，请返回或手动选择其它资源。"
                                statusHost?.show("切换失败", "当前已经是最后一个片源。", timeoutMs = 2600L)
                            }
                        }
                    },
                )
            }
        }

        // 统一底部渐变背景 (赋予沉浸感)
        if (anyOverlayVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxSize(0.6f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xEA000000)),
                            startY = 0f,
                        )
                    )
            )
        }

        // 控制面板
        if (showControls) {
            PlayerBottomOverlayScaffold(
                modifier = Modifier.align(Alignment.BottomCenter),
                title = title.ifBlank { "视频播放" },
                status = if (isBuffering) "缓冲中" else if (isPlaying) "播放中" else "已暂停",
                contentPaddingBottom = contentPaddingBottom,
                contentPaddingH = contentPaddingH,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = formatTime(positionMs),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        color = Color.White.copy(alpha = 0.9f),
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            .clip(CircleShape),
                    ) {
                        val progressRatio = if (durationMs > 0) {
                            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressRatio)
                                .fillMaxSize()
                                .background(Color.White),
                        )
                    }

                    Text(
                        text = formatTime(durationMs),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    PlayerControlButton(text = "返回", onClick = ::persistHistoryAndBack, modifier = Modifier.weight(1f))
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
                            keepOverlayAlive()
                        },
                        onFocused = ::keepOverlayAlive,
                        selected = isPlaying,
                        modifier = Modifier.weight(1f).focusRequester(playPauseFocusRequester),
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
                        text = "倍速",
                        onClick = {
                            val nextSpeed = when (playbackSpeed) {
                                1.0f -> 1.25f
                                1.25f -> 1.5f
                                1.5f -> 2.0f
                                else -> 1.0f
                            }
                            applyPlaybackSpeed(nextSpeed)
                            statusHost?.show("倍速切换", "当前为 ${formatSpeedLabel(nextSpeed)}")
                            keepOverlayAlive()
                        },
                        onFocused = ::keepOverlayAlive,
                        modifier = Modifier.weight(1f),
                        subtitle = formatSpeedLabel(playbackSpeed),
                    )
                }
            }
        }

        if (showEpisodePicker) {
            PlayerEpisodePicker(
                contentPaddingBottom = contentPaddingBottom,
                contentPaddingH = contentPaddingH,
                currentEpisodeIndex = currentEpisodeIndex,
                currentSourceIndex = currentSourceIndex,
                episodeChipRequesters = episodeChipRequesters,
                episodeColumns = maxOf(layout.episodeColumns, 8),
                keepOverlayAlive = ::keepOverlayAlive,
                onPlaySourceEpisode = { s, e ->
                    scope.launch { playSourceEpisode(s, e) }
                },
                sourceChipRequesters = sourceChipRequesters,
                sources = sources,
                currentEpisodes = pickerEpisodes,
                title = title.ifBlank { "视频播放" },
            )
        }
    }
}

@Composable
private fun PlayerBottomOverlayScaffold(
    modifier: Modifier = Modifier,
    title: String,
    status: String,
    contentPaddingBottom: androidx.compose.ui.unit.Dp,
    contentPaddingH: androidx.compose.ui.unit.Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = contentPaddingH, vertical = contentPaddingBottom),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(end = 24.dp),
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }

            content()
    }
}

@Composable
private fun PlayerErrorDialog(
    errorMessage: String,
    errorBackFocusRequester: FocusRequester,
    errorRetryFocusRequester: FocusRequester,
    onBack: () -> Unit,
    onFocused: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .background(Color(0xD9101010), RoundedCornerShape(24.dp))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
            .padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "无法播放该资源",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PlayerControlButton(
                text = "返回",
                onClick = onBack,
                onFocused = onFocused,
                modifier = Modifier
                    .width(120.dp)
                    .focusRequester(errorBackFocusRequester)
                    .focusProperties {
                        right = errorRetryFocusRequester
                    },
            )
            PlayerControlButton(
                text = "切换源",
                onClick = onRetry,
                onFocused = onFocused,
                selected = true,
                modifier = Modifier
                    .width(120.dp)
                    .focusRequester(errorRetryFocusRequester)
                    .focusProperties {
                        left = errorBackFocusRequester
                    },
            )
        }
    }
}

@Composable
private fun BoxScope.PlayerEpisodePicker(
    title: String,
    contentPaddingBottom: androidx.compose.ui.unit.Dp,
    contentPaddingH: androidx.compose.ui.unit.Dp,
    currentEpisodeIndex: Int,
    currentSourceIndex: Int,
    episodeChipRequesters: List<FocusRequester>,
    episodeColumns: Int,
    keepOverlayAlive: () -> Unit,
    onPlaySourceEpisode: (Int, Int) -> Unit,
    sourceChipRequesters: List<FocusRequester>,
    sources: List<TvPlaySource>,
    currentEpisodes: List<TvEpisode>,
) {
    val currentSource = sources.getOrNull(currentSourceIndex)
    val episodeScrollState = rememberScrollState()
    PlayerBottomOverlayScaffold(
        modifier = Modifier.align(Alignment.BottomCenter),
        title = title,
        status = "菜单 · ${currentSource?.name.orEmpty().ifBlank { "当前线路" }}",
        contentPaddingBottom = contentPaddingBottom,
        contentPaddingH = contentPaddingH,
    ) {
        if (sources.isNotEmpty()) {
            val currentEpisodeTargetIndex = currentEpisodeIndex.coerceIn(
                0,
                (currentEpisodes.size - 1).coerceAtLeast(0),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "线路切换",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                LazyRow(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().focusGroup(),
                ) {
                    itemsIndexed(sources, key = { index, source -> "${source.code}-$index" }) { index, source ->
                        TvFocusChip(
                            text = source.name,
                            selected = index == currentSourceIndex,
                            minWidth = 118.dp,
                            keepVisibleOnFocus = false,
                            modifier = Modifier
                                .focusRequester(sourceChipRequesters[index])
                                .zIndex(if (index == currentSourceIndex) 1f else 0f)
                                .focusProperties {
                                    if (index > 0) left = sourceChipRequesters[index - 1]
                                    if (index < sources.lastIndex) right = sourceChipRequesters[index + 1]
                                    if (currentEpisodes.isNotEmpty()) down = episodeChipRequesters[currentEpisodeTargetIndex]
                                }
                                .onFocusChanged { if (it.isFocused) keepOverlayAlive() },
                            onClick = {
                                keepOverlayAlive()
                                onPlaySourceEpisode(index, 0)
                            },
                        )
                    }
                }
            }
        }

        if (currentEpisodes.isEmpty()) {
            Text(
                text = "正在获取集数...",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            val currentSourceTargetIndex = currentSourceIndex.coerceIn(
                0,
                (sources.size - 1).coerceAtLeast(0),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "剧集列表",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val itemSpacing = 12.dp
                    val columns = episodeColumns.coerceAtLeast(1)
                    val itemWidth = (maxWidth - itemSpacing * (columns - 1)) / columns

                    FlowRow(
                        maxItemsInEachRow = columns,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(episodeScrollState)
                            .focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                        verticalArrangement = Arrangement.spacedBy(itemSpacing),
                    ) {
                        currentEpisodes.forEachIndexed { index, episode ->
                            val columnIndex = index % columns
                            val leftIndex = if (columnIndex > 0) index - 1 else null
                            val rightIndex = if (columnIndex < columns - 1 && index < currentEpisodes.lastIndex) index + 1 else null
                            val upIndex = (index - columns).takeIf { it >= 0 }
                            val downIndex = (index + columns).takeIf { it < currentEpisodes.size }
                            TvFocusChip(
                                text = episode.name,
                                selected = index == currentEpisodeIndex,
                                minWidth = itemWidth,
                                keepVisibleOnFocus = true,
                                modifier = Modifier
                                    .width(itemWidth)
                                    .focusRequester(episodeChipRequesters[index])
                                    .zIndex(if (index == currentEpisodeIndex) 1f else 0f)
                                    .focusProperties {
                                        leftIndex?.let { left = episodeChipRequesters[it] }
                                        rightIndex?.let { right = episodeChipRequesters[it] }
                                        if (upIndex != null) {
                                            up = episodeChipRequesters[upIndex]
                                        } else if (sources.isNotEmpty()) {
                                            up = sourceChipRequesters[currentSourceTargetIndex]
                                        }
                                        downIndex?.let { down = episodeChipRequesters[it] }
                                    }
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            keepOverlayAlive()
                                        }
                                    },
                                onClick = {
                                    keepOverlayAlive()
                                    onPlaySourceEpisode(currentSourceIndex, index)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatSpeedLabel(speed: Float): String {
    val normalized = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()
    return "${normalized}x"
}

@Composable
private fun PlayerControlButton(
    text: String,
    onClick: () -> Unit,
    onFocused: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    subtitle: String? = null
) {
    var focused by remember { mutableStateOf(false) }
    // 采用更贴近 Apple TV 的胶囊型微圆角
    val shape = RoundedCornerShape(14.dp) 
    
    val containerColor = when {
        selected && !focused -> Color.White
        focused -> Color.White
        else -> Color(0x33FFFFFF) // 毛玻璃半透明底
    }
    
    val borderColor = when {
        focused -> Color.Transparent
        selected -> Color.Transparent
        else -> Color(0x1AFFFFFF) // 极浅的微光边框
    }
    
    val textColor = when {
        selected || focused -> Color.Black
        else -> Color.White
    }
    
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "btn_scale"
    )

    Surface(
        onClick = onClick,
        shape = shape,
        color = containerColor,
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .consumeRepeatedDpadEvents()
            .ensureVisibleOnFocus()
            .height(56.dp)
            .border(if (focused) 0.dp else 1.dp, borderColor, shape)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = textColor,
                maxLines = 1,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = textColor.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
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

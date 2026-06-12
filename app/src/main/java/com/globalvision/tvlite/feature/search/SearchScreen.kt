package com.globalvision.tvlite.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.globalvision.tvlite.core.model.TvPosterItem
import com.globalvision.tvlite.core.model.TvSearchKeywordGroup
import com.globalvision.tvlite.core.model.TvSearchKeywordItem
import com.globalvision.tvlite.core.network.TvRepository
import com.globalvision.tvlite.feature.common.consumeRepeatedDpadEvents
import com.globalvision.tvlite.feature.common.isRepeatedDpadEvent
import com.globalvision.tvlite.feature.common.TvFeedbackPanel
import com.globalvision.tvlite.feature.common.TvLoadingPanel
import com.globalvision.tvlite.feature.common.TvFocusChip
import com.globalvision.tvlite.feature.common.TvPosterCard
import com.globalvision.tvlite.feature.common.TvScreenScaffold
import com.globalvision.tvlite.feature.common.LocalTvStatusHostState
import com.globalvision.tvlite.ui.theme.TvFocusBorder
import com.globalvision.tvlite.ui.theme.rememberTvLayoutMetrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

private enum class SearchFocusArea {
    SearchField,
    Results,
}

private data class CachedSearchContent(
    val keyword: String,
    val items: List<TvPosterItem>,
    val nextPage: Int,
    val total: Int,
    val hasMore: Boolean,
)

@Composable
fun SearchScreen(
    repository: TvRepository,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val layout = rememberTvLayoutMetrics()
    val statusHost = LocalTvStatusHostState.current
    var keyword by rememberSaveable { mutableStateOf("") }
    var activeKeyword by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var searchToken by remember { mutableIntStateOf(0) }
    var pendingSearchKeyword by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<TvPosterItem>>(emptyList()) }
    var totalCount by remember { mutableIntStateOf(0) }
    var nextPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var focusArea by remember { mutableStateOf(SearchFocusArea.SearchField) }
    var pendingFocusFirstResult by remember { mutableStateOf(false) }
    val searchCache = remember { mutableStateMapOf<String, CachedSearchContent>() }
    var localRecentKeywords by remember { mutableStateOf<List<String>>(emptyList()) }
    var serverRecentKeywords by remember { mutableStateOf<List<TvSearchKeywordItem>>(emptyList()) }
    var rankingSections by remember { mutableStateOf<List<TvSearchKeywordGroup>>(emptyList()) }
    var autocompleteKeywords by remember { mutableStateOf<List<TvSearchKeywordItem>>(emptyList()) }

    val searchFieldRequester = remember { FocusRequester() }
    val firstHistoryRequester = remember { FocusRequester() }
    val firstAutocompleteRequester = remember { FocusRequester() }
    val firstResultRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val moduleScrollState = rememberScrollState()
    val rankingSectionRequesters = remember(rankingSections.size) {
        List(rankingSections.size) { FocusRequester() }
    }

    fun applyCache(cache: CachedSearchContent) {
        results = cache.items
        totalCount = cache.total
        nextPage = cache.nextPage
        hasMore = cache.hasMore
    }

    fun recordRecentKeyword(value: String) {
        val normalized = value.trim()
        if (normalized.isBlank()) return
        localRecentKeywords = listOf(normalized) + localRecentKeywords.filterNot { it.equals(normalized, ignoreCase = true) }
            .take(7)
    }

    fun startSearch(rawKeyword: String, forceRefresh: Boolean = false, focusFirstResult: Boolean = false) {
        val normalized = rawKeyword.trim()
        if (normalized.isBlank()) return
        keyword = normalized
        activeKeyword = normalized
        recordRecentKeyword(normalized)
        autocompleteKeywords = emptyList()
        statusHost?.show("开始搜索", "正在搜索“$normalized”。")

        if (!forceRefresh) {
            searchCache[normalized]?.let { cached ->
                applyCache(cached)
                pendingFocusFirstResult = focusFirstResult && cached.items.isNotEmpty()
                if (cached.items.isEmpty()) {
                    focusArea = SearchFocusArea.SearchField
                }
                pendingSearchKeyword = null
                searchToken = 0
                return
            }
        }

        pendingSearchKeyword = normalized
        searchToken += 1
        pendingFocusFirstResult = focusFirstResult
    }

    suspend fun loadMoreResults() {
        val normalized = activeKeyword.trim()
        if (normalized.isBlank() || loading || loadingMore || !hasMore) return

        loadingMore = true
        val pageToLoad = nextPage
        val response = try {
            repository.search(normalized, pageToLoad)
        } catch (_: Throwable) {
            statusHost?.show("加载更多失败", "搜索结果分页请求失败，请稍后再试。", timeoutMs = 2600L)
            null
        }

        if (response != null) {
            val merged = results + response.items
            val more = merged.size < response.total && response.items.isNotEmpty()
            results = merged
            totalCount = response.total
            nextPage = pageToLoad + 1
            hasMore = more
            searchCache[normalized] = CachedSearchContent(
                keyword = normalized,
                items = merged,
                nextPage = nextPage,
                total = totalCount,
                hasMore = hasMore,
            )
        } else {
            hasMore = false
        }
        loadingMore = false
    }

    LaunchedEffect(Unit) {
        delay(32)
        searchFieldRequester.requestFocus()
    }

    LaunchedEffect(Unit) {
        rankingSections = runCatching { repository.getSearchRanking() }
            .getOrDefault(emptyList())
        serverRecentKeywords = runCatching { repository.getSearchLatelyWords() }
            .getOrDefault(emptyList())
    }

    LaunchedEffect(keyword) {
        val normalized = keyword.trim()
        if (normalized.isBlank()) {
            autocompleteKeywords = emptyList()
            if (activeKeyword.isBlank()) {
                results = emptyList()
                totalCount = 0
                nextPage = 1
                hasMore = false
                pendingFocusFirstResult = false
            }
            return@LaunchedEffect
        }

        if (normalized == activeKeyword) {
            return@LaunchedEffect
        }

        delay(320)
        if (keyword.trim() != normalized) return@LaunchedEffect
        autocompleteKeywords = runCatching { repository.getSearchAutocomplete(normalized) }
            .getOrDefault(emptyList())
    }

    LaunchedEffect(searchToken) {
        if (searchToken == 0) return@LaunchedEffect
        loading = true
        val normalized = pendingSearchKeyword?.trim().orEmpty()
        if (normalized.isBlank()) {
            loading = false
            return@LaunchedEffect
        }
        val response = try {
            repository.search(normalized, page = 1)
        } catch (_: Throwable) {
            null
        }
        loading = false
        results = response?.items.orEmpty()
        totalCount = response?.total ?: 0
        nextPage = 2
        hasMore = response != null && response.items.size < response.total && response.items.isNotEmpty()
        if (response != null) {
            statusHost?.show("搜索完成", "已找到 ${response.total} 条与“$normalized”相关的内容。")
            searchCache[normalized] = CachedSearchContent(
                keyword = normalized,
                items = response.items,
                nextPage = nextPage,
                total = totalCount,
                hasMore = hasMore,
            )
        } else {
            statusHost?.show("搜索失败", "当前网络较慢，请稍后重试。", timeoutMs = 2600L)
        }
        pendingSearchKeyword = null
    }

    LaunchedEffect(results, pendingFocusFirstResult) {
        if (pendingFocusFirstResult && results.isNotEmpty()) {
            delay(32)
            firstResultRequester.requestFocus()
            focusArea = SearchFocusArea.Results
            pendingFocusFirstResult = false
        }
    }

    LaunchedEffect(gridState, results, hasMore, loading, loadingMore) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                // 适配 5 列布局阈值
                if (
                    lastVisibleIndex >= maxOf(results.lastIndex - 5, 0) &&
                    hasMore &&
                    !loading &&
                    !loadingMore
                ) {
                    loadMoreResults()
                }
            }
    }

    BackHandler {
        if (focusArea == SearchFocusArea.Results) {
            searchFieldRequester.requestFocus()
            focusArea = SearchFocusArea.SearchField
        } else {
            onBack()
        }
    }

    val recentKeywords = remember(localRecentKeywords, serverRecentKeywords) {
        (localRecentKeywords.map { TvSearchKeywordItem(it) } + serverRecentKeywords)
            .filter { it.word.isNotBlank() }
            .distinctBy { it.word.lowercase() }
            .take(8)
    }

    TvScreenScaffold(
        title = "",
        onBack = null,
        showTitle = false,
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 输入框高质感悬浮呼吸态管理
            var isSearchFocused by remember { mutableStateOf(false) }
            val searchBarScale by animateFloatAsState(
                targetValue = if (isSearchFocused) 1.02f else 1.0f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
                label = "search_bar_scale"
            )
            val searchBarShape = RoundedCornerShape(16.dp)

            // ================= 重构：Apple TV 极简胶囊搜索栏 =================
            OutlinedTextField(
                value = keyword,
                onValueChange = { value ->
                    keyword = value
                    if (value.trim() != activeKeyword) {
                        activeKeyword = ""
                        results = emptyList()
                        totalCount = 0
                        nextPage = 1
                        hasMore = false
                        pendingFocusFirstResult = false
                        pendingSearchKeyword = null
                        loading = false
                    }
                },
                placeholder = { 
                    Text(
                        text = "输入影片名称、导演或演员...",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            color = if (isSearchFocused) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.35f)
                        )
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White, // 获焦全变白
                    unfocusedContainerColor = Color.White.copy(alpha = 0.06f), // 极具质感的非获焦透明磨砂黑
                    focusedTextColor = Color.Black, // 获焦黑字，绝不撞色
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = TvFocusBorder,
                    unfocusedBorderColor = Color.Transparent, // 摒弃粗糙的移动端物理边框线
                    cursorColor = Color.Black,
                ),
                shape = searchBarShape,
                modifier = Modifier
                    .consumeRepeatedDpadEvents()
                    .fillMaxWidth()
                    .height(64.dp) // 极度适合 TV 视距的饱满厚度
                    .graphicsLayer {
                        scaleX = searchBarScale
                        scaleY = searchBarScale
                    }
                    .shadow(
                        elevation = if (isSearchFocused) 8.dp else 0.dp,
                        shape = searchBarShape,
                        ambientColor = Color.Black.copy(alpha = 0.4f),
                        spotColor = Color.Black.copy(alpha = 0.3f)
                    )
                    .focusRequester(searchFieldRequester)
                    .focusProperties {
                        when {
                            keyword.isBlank() -> {
                                if (rankingSections.isNotEmpty()) {
                                    down = rankingSectionRequesters.firstOrNull() ?: firstHistoryRequester
                                } else if (recentKeywords.isNotEmpty()) {
                                    down = firstHistoryRequester
                                }
                            }
                            autocompleteKeywords.isNotEmpty() -> down = firstAutocompleteRequester
                            activeKeyword.isNotBlank() && results.isNotEmpty() -> down = firstResultRequester
                        }
                    }
                    .onFocusChanged {
                        isSearchFocused = it.isFocused
                        if (it.isFocused) {
                            focusArea = SearchFocusArea.SearchField
                        }
                    }
                    .onPreviewKeyEvent { event -> event.isRepeatedDpadEvent() },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        startSearch(keyword, forceRefresh = true, focusFirstResult = true)
                    }
                ),
                )
            if (keyword.isBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.95f)
                        .verticalScroll(moduleScrollState)
                        .focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    rankingSections.forEachIndexed { index, section ->
                        val currentRequester = rankingSectionRequesters.getOrNull(index)
                        val previousRequester = if (index == 0) searchFieldRequester else rankingSectionRequesters.getOrNull(index - 1)
                        val nextRequester = rankingSectionRequesters.getOrNull(index + 1) ?: firstHistoryRequester
                        SearchHintRowSection(
                            title = section.title,
                            keywords = section.items,
                            firstRequester = currentRequester ?: firstHistoryRequester,
                            upRequester = previousRequester,
                            downRequester = nextRequester,
                            onSelect = { selected ->
                                startSearch(selected.word, forceRefresh = true, focusFirstResult = true)
                            },
                        )
                    }
                    SearchHintRowSection(
                        title = "最近搜索",
                        keywords = recentKeywords,
                        firstRequester = firstHistoryRequester,
                        upRequester = rankingSectionRequesters.lastOrNull() ?: searchFieldRequester,
                        onSelect = { selected ->
                            startSearch(selected.word, forceRefresh = true, focusFirstResult = true)
                        },
                    )
                    if (loading) {
                        TvLoadingPanel(message = "正在准备搜索面板...", centered = true)
                    }
                }
            } else {
                when {
                    activeKeyword.isBlank() && autocompleteKeywords.isNotEmpty() -> {
                        SearchHintRowSection(
                            title = "搜索联想",
                            keywords = autocompleteKeywords,
                            firstRequester = firstAutocompleteRequester,
                            upRequester = searchFieldRequester,
                            onSelect = { selected ->
                                startSearch(selected.word, forceRefresh = true, focusFirstResult = true)
                            },
                        )
                    }
                }

                    when {
                        loading -> {
                        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.95f), contentAlignment = Alignment.Center) {
                            TvLoadingPanel(
                                message = if (activeKeyword.isNotBlank()) "正在搜索“$activeKeyword”..." else "正在准备搜索面板...",
                                centered = true,
                            )
                        }
                    }
                    results.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.95f), contentAlignment = Alignment.Center) {
                            TvFeedbackPanel(
                                title = "没有找到匹配内容",
                                message = "可以尝试选择上方热搜词，或者换一个更短的关键词。",
                            )
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(5),
                            contentPadding = PaddingValues(bottom = 60.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.95f)
                                .focusGroup(),
                        ) {
                            itemsIndexed(results, key = { _, item -> item.id }) { index, item ->
                                val isTopRow = index < 5
                                TvPosterCard(
                                    item = item,
                                    width = 188.dp,
                                    compact = true,
                                    modifier = Modifier
                                        .then(
                                            if (index == 0) Modifier.focusRequester(firstResultRequester)
                                            else Modifier
                                        )
                                        .focusProperties {
                                            if (isTopRow) up = searchFieldRequester
                                        },
                                    onClick = { onOpenDetail(item.id) },
                                )
                            }

                            if (loadingMore) {
                                item(span = { GridItemSpan(5) }) {
                                    TvLoadingPanel(message = "正在加载更多搜索结果...")
                                }
                            } else if (!hasMore && results.isNotEmpty()) {
                                item(span = { GridItemSpan(5) }) {
                                    TvFeedbackPanel(
                                        title = "结果已经到底",
                                        message = "已经把当前关键词的可见内容全部加载完成。",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHintGridSection(
    title: String,
    keywords: List<TvSearchKeywordItem>,
    firstRequester: FocusRequester?,
    onSelect: (TvSearchKeywordItem) -> Unit,
) {
    if (keywords.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = Color.White.copy(alpha = 0.88f),
        )
        FlowRow(
            maxItemsInEachRow = 8,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().focusGroup(),
        ) {
            keywords.forEachIndexed { index, item ->
                TvFocusChip(
                    text = item.word,
                    minWidth = 118.dp,
                    modifier = if (index == 0 && firstRequester != null) Modifier.focusRequester(firstRequester) else Modifier,
                    onClick = { onSelect(item) },
                )
            }
        }
    }
}

@Composable
private fun SearchHintRowSection(
    title: String,
    keywords: List<TvSearchKeywordItem>,
    firstRequester: FocusRequester,
    upRequester: FocusRequester? = null,
    downRequester: FocusRequester? = null,
    onSelect: (TvSearchKeywordItem) -> Unit,
) {
    if (keywords.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = Color.White.copy(alpha = 0.88f),
        )
        FlowRow(
            maxItemsInEachRow = 8,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().focusGroup(),
        ) {
            keywords.forEachIndexed { index, item ->
                TvFocusChip(
                    text = item.word,
                    minWidth = 118.dp,
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(firstRequester) else Modifier)
                        .focusProperties {
                            if (upRequester != null) up = upRequester
                            if (downRequester != null) down = downRequester
                        },
                    onClick = { onSelect(item) },
                )
            }
        }
    }
}

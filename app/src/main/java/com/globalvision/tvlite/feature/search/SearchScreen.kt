package com.globalvision.tvlite.feature.search

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.globalvision.tvlite.core.model.TvPosterItem
import com.globalvision.tvlite.core.model.TvSearchKeywordGroup
import com.globalvision.tvlite.core.model.TvSearchKeywordItem
import com.globalvision.tvlite.core.network.TvRepository
import com.globalvision.tvlite.feature.common.consumeRepeatedDpadEvents
import com.globalvision.tvlite.feature.common.isRepeatedDpadEvent
import com.globalvision.tvlite.feature.common.TvFeedbackPanel
import com.globalvision.tvlite.feature.common.TvLoadingPanel
import com.globalvision.tvlite.feature.common.TvPosterImage
import com.globalvision.tvlite.feature.common.TvScreenScaffold
import com.globalvision.tvlite.feature.common.LocalTvStatusHostState
import com.globalvision.tvlite.feature.common.tvFocusBorder
import com.globalvision.tvlite.ui.theme.TvFocusBorder
import com.globalvision.tvlite.ui.theme.rememberTvLayoutMetrics
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

private enum class SearchFocusArea {
    SearchField,
    Results,
}

private const val SearchResultsColumns = 5
private const val SearchLogTag = "SearchScreen"

private data class CachedSearchContent(
    val keyword: String,
    val items: List<TvPosterItem>,
    val nextPage: Int,
    val pageSize: Int,
    val total: Int,
    val hasMore: Boolean,
)

private data class SearchSectionMeta(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val accent: Color,
    val secondaryAccent: Color,
    val subtitle: String = "",
)

@Composable
fun SearchScreen(
    repository: TvRepository,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val layout = rememberTvLayoutMetrics()
    val statusHost = LocalTvStatusHostState.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val sectionMetas = remember {
        mapOf(
            "热搜" to SearchSectionMeta(Icons.AutoMirrored.Filled.TrendingUp, Color(0xFFFF7A45), Color(0xFFFFB347), "大家都在搜"),
            "最近搜索" to SearchSectionMeta(Icons.Filled.History, Color(0xFF7CAEFF), Color(0xFF5C8DFF), "继续浏览"),
            "搜索联想" to SearchSectionMeta(Icons.Filled.AutoAwesome, Color(0xFF72B6FF), Color(0xFF9E8BFF), "猜你想搜"),
        )
    }
    var keyword by rememberSaveable { mutableStateOf("") }
    var activeKeyword by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var initialPanelLoading by remember { mutableStateOf(true) }
    var searchToken by remember { mutableIntStateOf(0) }
    var pendingSearchKeyword by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<TvPosterItem>>(emptyList()) }
    var totalCount by remember { mutableIntStateOf(0) }
    var nextPage by remember { mutableIntStateOf(1) }
    var currentPageSize by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var focusedResultIndex by remember { mutableIntStateOf(-1) }
    var focusArea by remember { mutableStateOf(SearchFocusArea.SearchField) }
    var pendingFocusFirstResult by remember { mutableStateOf(false) }
    val searchCache = remember { mutableStateMapOf<String, CachedSearchContent>() }
    var localRecentKeywords by remember { mutableStateOf<List<String>>(emptyList()) }
    var serverRecentKeywords by remember { mutableStateOf<List<TvSearchKeywordItem>>(emptyList()) }
    var rankingSections by remember { mutableStateOf<List<TvSearchKeywordGroup>>(emptyList()) }
    var autocompleteKeywords by remember { mutableStateOf<List<TvSearchKeywordItem>>(emptyList()) }
    var shouldAnimateSearchFocus by rememberSaveable { mutableStateOf(false) }

    val searchFieldRequester = remember { FocusRequester() }
    val firstHistoryRequester = remember { FocusRequester() }
    val firstAutocompleteRequester = remember { FocusRequester() }
    val firstResultRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val moduleScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val rankingSectionRequesters = remember(rankingSections.size) {
        List(rankingSections.size) { FocusRequester() }
    }

    fun applyCache(cache: CachedSearchContent) {
        results = cache.items
        totalCount = cache.total
        nextPage = cache.nextPage
        currentPageSize = cache.pageSize
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
        if (normalized.isBlank() || loading || loadingMore || !hasMore) {
            return
        }

        loadingMore = true
        val pageToLoad = nextPage
        val response = try {
            repository.search(normalized, pageToLoad)
        } catch (throwable: Throwable) {
            Log.e(SearchLogTag, "loadMore exception: keyword=$normalized page=$pageToLoad", throwable)
            statusHost?.show("加载更多失败", "搜索结果分页请求失败，请稍后再试。", timeoutMs = 2600L)
            null
        }

        if (response != null) {
            val merged = results + response.items
            val resolvedPageSize = response.pageSize.takeIf { it > 0 } ?: response.items.size
            val resolvedNextPage = response.page + 1
            val more = when {
                response.total <= 0 -> false
                resolvedPageSize <= 0 -> merged.size < response.total && response.items.isNotEmpty()
                else -> response.page * resolvedPageSize < response.total && response.items.isNotEmpty()
            }
            results = merged
            totalCount = response.total
            nextPage = resolvedNextPage
            currentPageSize = resolvedPageSize
            hasMore = more
            searchCache[normalized] = CachedSearchContent(
                keyword = normalized,
                items = merged,
                nextPage = nextPage,
                pageSize = currentPageSize,
                total = totalCount,
                hasMore = hasMore,
            )
        } else {
            hasMore = false
        }
        loadingMore = false
    }

    fun requestLoadMore() {
        scope.launch {
            loadMoreResults()
        }
    }

    LaunchedEffect(Unit) {
        delay(32)
        searchFieldRequester.requestFocus()
        keyboardController?.hide()
    }

    LaunchedEffect(Unit) {
        initialPanelLoading = true
        rankingSections = runCatching { repository.getSearchRanking() }
            .getOrDefault(emptyList())
        serverRecentKeywords = runCatching { repository.getSearchLatelyWords() }
            .getOrDefault(emptyList())
        initialPanelLoading = false
    }

    LaunchedEffect(keyword) {
        val normalized = keyword.trim()
        if (normalized.isBlank()) {
            autocompleteKeywords = emptyList()
            if (activeKeyword.isBlank()) {
                results = emptyList()
                totalCount = 0
                nextPage = 1
                currentPageSize = 0
                hasMore = false
                focusedResultIndex = -1
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
        if (pendingSearchKeyword?.trim().orEmpty() != normalized && keyword.trim() != normalized) {
            loading = false
            return@LaunchedEffect
        }
        loading = false
        results = response?.items.orEmpty()
        totalCount = response?.total ?: 0
        currentPageSize = response?.pageSize?.takeIf { it > 0 } ?: response?.items?.size ?: 0
        nextPage = (response?.page ?: 1) + 1
        focusedResultIndex = -1
        hasMore = when {
            response == null -> false
            response.total <= 0 -> false
            currentPageSize <= 0 -> response.items.size < response.total && response.items.isNotEmpty()
            else -> response.page * currentPageSize < response.total && response.items.isNotEmpty()
        }
        if (response != null) {
            statusHost?.show("搜索完成", "已找到 ${response.total} 条与“$normalized”相关的内容。")
            searchCache[normalized] = CachedSearchContent(
                keyword = normalized,
                items = response.items,
                nextPage = nextPage,
                pageSize = currentPageSize,
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

    LaunchedEffect(activeKeyword, hasMore, loading, loadingMore) {
        snapshotFlow {
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalCount = gridState.layoutInfo.totalItemsCount
            lastVisibleIndex to totalCount
        }.distinctUntilChanged()
            .collect { (lastVisibleIndex, totalCount) ->
                if (loading || loadingMore || !hasMore || totalCount == 0) return@collect
                val threshold = 12
                if (lastVisibleIndex >= totalCount - threshold) {
                    requestLoadMore()
                }
            }
    }

    LaunchedEffect(focusedResultIndex, hasMore, loading, loadingMore, results.size) {
        if (focusedResultIndex < 0 || !hasMore || loading || loadingMore) return@LaunchedEffect
        val remainingItems = results.size - 1 - focusedResultIndex
        if (remainingItems < SearchResultsColumns * 2) {
            requestLoadMore()
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
    val isEditingNewQuery = keyword.trim().isNotBlank() && keyword.trim() != activeKeyword

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
            var isSearchFocused by remember { mutableStateOf(false) }
            val searchBarScale by animateFloatAsState(
                targetValue = if (isSearchFocused && shouldAnimateSearchFocus) 1.008f else 1.0f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
                label = "search_bar_scale",
            )
            val searchBarShape = RoundedCornerShape(16.dp)

            OutlinedTextField(
                value = keyword,
                onValueChange = { value ->
                    keyword = value
                    pendingSearchKeyword = null
                    searchToken = 0
                    if (value.isBlank()) {
                        activeKeyword = ""
                        results = emptyList()
                        totalCount = 0
                        nextPage = 1
                        currentPageSize = 0
                        hasMore = false
                        focusedResultIndex = -1
                        pendingFocusFirstResult = false
                        autocompleteKeywords = emptyList()
                        loading = false
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(26.dp),
                    )
                },
                placeholder = {
                    Text(
                        text = "搜索影片 / 演员 / 导演",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            color = Color.White.copy(alpha = 0.42f),
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1A2638).copy(alpha = 0.94f),
                    unfocusedContainerColor = Color(0xFF172233).copy(alpha = 0.92f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = TvFocusBorder,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
                    cursorColor = Color.White,
                ),
                shape = searchBarShape,
                modifier = Modifier
                    .consumeRepeatedDpadEvents()
                    .fillMaxWidth()
                    .height(60.dp)
                    .graphicsLayer {
                        scaleX = searchBarScale
                        scaleY = searchBarScale
                    }
                    .shadow(
                        elevation = if (isSearchFocused) 18.dp else 8.dp,
                        shape = searchBarShape,
                        ambientColor = if (isSearchFocused) Color(0x8069A7FF) else Color.Black.copy(alpha = 0.28f),
                        spotColor = if (isSearchFocused) Color(0xFF6EA8FF) else Color.Black.copy(alpha = 0.18f),
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
                            isEditingNewQuery && autocompleteKeywords.isNotEmpty() -> down = firstAutocompleteRequester
                            !isEditingNewQuery && activeKeyword.isNotBlank() && results.isNotEmpty() -> down = firstResultRequester
                        }
                    }
                    .onFocusChanged {
                        isSearchFocused = it.isFocused
                        if (it.isFocused) {
                            focusArea = SearchFocusArea.SearchField
                            keyboardController?.hide()
                        } else {
                            shouldAnimateSearchFocus = true
                        }
                    }
                    .onPreviewKeyEvent { event -> event.isRepeatedDpadEvent() },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        startSearch(keyword, forceRefresh = true, focusFirstResult = true)
                    },
                ),
            )

            if (keyword.isBlank()) {
                if (initialPanelLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.95f),
                        contentAlignment = Alignment.Center,
                    ) {
                        TvLoadingPanel(message = "正在准备搜索面板...", centered = true)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.95f)
                            .verticalScroll(moduleScrollState)
                            .focusGroup(),
                        verticalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        rankingSections.forEachIndexed { index, section ->
                            val currentRequester = rankingSectionRequesters.getOrNull(index)
                            val previousRequester = if (index == 0) searchFieldRequester else rankingSectionRequesters.getOrNull(index - 1)
                            val nextRequester = rankingSectionRequesters.getOrNull(index + 1) ?: firstHistoryRequester
                            SearchHintRowSection(
                                title = section.title,
                                keywords = section.items,
                                meta = sectionMetas[section.title] ?: SearchSectionMeta(
                                    icon = Icons.Filled.LocalMovies,
                                    accent = Color(0xFF6EA8FF),
                                    secondaryAccent = Color(0xFF6E89FF),
                                    subtitle = "热门推荐",
                                ),
                                firstRequester = currentRequester ?: firstHistoryRequester,
                                upRequester = previousRequester,
                                downRequester = nextRequester,
                                isRanking = true,
                                onSelect = { selected ->
                                    startSearch(selected.word, forceRefresh = true, focusFirstResult = true)
                                },
                            )
                        }
                        SearchHintRowSection(
                            title = "最近搜索",
                            keywords = recentKeywords,
                            meta = sectionMetas.getValue("最近搜索"),
                            firstRequester = firstHistoryRequester,
                            upRequester = rankingSectionRequesters.lastOrNull() ?: searchFieldRequester,
                            isRanking = false,
                            onSelect = { selected ->
                                startSearch(selected.word, forceRefresh = true, focusFirstResult = true)
                            },
                        )
                    }
                }
            } else {
                when {
                    isEditingNewQuery && autocompleteKeywords.isNotEmpty() -> {
                        SearchHintRowSection(
                            title = "搜索联想",
                            keywords = autocompleteKeywords,
                            meta = sectionMetas.getValue("搜索联想"),
                            firstRequester = firstAutocompleteRequester,
                            upRequester = searchFieldRequester,
                            isRanking = false,
                            onSelect = { selected ->
                                startSearch(selected.word, forceRefresh = true, focusFirstResult = true)
                            },
                        )
                    }
                    isEditingNewQuery -> {
                        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.95f), contentAlignment = Alignment.Center) {
                            TvFeedbackPanel(
                                title = "准备搜索新关键词",
                                message = "按搜索键开始搜索，或继续输入以获取联想词。",
                            )
                        }
                    }
                }

                when {
                    isEditingNewQuery -> Unit
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.95f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = buildString {
                                    append("共 ").append(totalCount).append(" 条")
                                    if (currentPageSize > 0) {
                                        append(" · 已加载 ").append(results.size).append(" 条")
                                        append(" · 每页 ").append(currentPageSize).append(" 条")
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.58f),
                                ),
                            )

                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Fixed(SearchResultsColumns),
                                contentPadding = PaddingValues(bottom = 60.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                verticalArrangement = Arrangement.spacedBy(24.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .focusGroup(),
                            ) {
                                itemsIndexed(results, key = { _, item -> item.id }) { index, item ->
                                    val isTopRow = index < 5
                                    SearchResultPosterCard(
                                        repository = repository,
                                        item = item,
                                        modifier = Modifier
                                            .then(
                                                if (index == 0) Modifier.focusRequester(firstResultRequester)
                                                else Modifier
                                            )
                                            .focusProperties {
                                                if (isTopRow) up = searchFieldRequester
                                            },
                                        onFocused = { focusedResultIndex = index },
                                        onClick = { onOpenDetail(item.id) },
                                    )
                                }

                                if (loadingMore) {
                                    item(span = { GridItemSpan(5) }) {
                                        TvLoadingPanel(message = "正在加载更多搜索结果...")
                                    }
                                } else if (!hasMore && results.isNotEmpty()) {
                                    item(span = { GridItemSpan(5) }) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 10.dp, bottom = 20.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "— 已经到底啦 —",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = 14.sp,
                                                    color = Color.White.copy(alpha = 0.3f),
                                                )
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
    }
}

@Composable
private fun SearchResultPosterCard(
    repository: TvRepository,
    item: TvPosterItem,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.01f else 1f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
        label = "search_result_poster_scale",
    )

    Column(
        modifier = modifier
            .consumeRepeatedDpadEvents()
            .zIndex(if (focused) 1f else 0f)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
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
                .aspectRatio(1.48f)
                .shadow(
                    elevation = if (focused) 18.dp else 0.dp,
                    shape = shape,
                    ambientColor = if (focused) Color(0x664C94FF) else Color.Black.copy(alpha = 0.38f),
                    spotColor = if (focused) Color(0xCC72A8FF) else Color.Black.copy(alpha = 0.20f),
                )
                .tvFocusBorder(focused = focused, shape = shape, width = 2.dp, unfocusedWidth = 0.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF152233)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                TvPosterImage(
                    posterUrl = item.posterUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    movieId = item.id,
                    repository = repository,
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x140A1D38),
                                    Color(0xA608111E),
                                ),
                                startY = 120f,
                            ),
                        ),
                )

                if (item.remark.isNotBlank()) {
                    val remarkBackground = if (focused) Color(0xFFE85D3C) else Color(0xCCEA6B41)
                    val remarkTextColor = Color.White
                    Text(
                        text = item.remark,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = remarkTextColor,
                        modifier = Modifier
                            .padding(8.dp)
                            .background(
                                color = remarkBackground,
                                shape = RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1,
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
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
                                color = Color.White.copy(alpha = 0.78f),
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHintRowSection(
    title: String,
    keywords: List<TvSearchKeywordItem>,
    meta: SearchSectionMeta,
    firstRequester: FocusRequester,
    upRequester: FocusRequester? = null,
    downRequester: FocusRequester? = null,
    isRanking: Boolean = false,
    onSelect: (TvSearchKeywordItem) -> Unit,
) {
    if (keywords.isEmpty()) return

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SearchSectionHeader(title = title, meta = meta)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val itemWidth = if (isRanking) 160.dp else 130.dp
            val itemHeight = if (isRanking) 64.dp else 56.dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 2.dp, vertical = 8.dp) // 增加了一点上下间距，避免发光阴影被直接切断
                    .focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                keywords.forEachIndexed { index, item ->
                    SearchKeywordPill(
                        item = item,
                        meta = meta,
                        index = index,
                        isRanking = isRanking,
                        itemWidth = itemWidth,
                        itemHeight = itemHeight,
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(firstRequester) else Modifier)
                            .focusProperties {
                                if (upRequester != null) up = upRequester
                                if (downRequester != null) down = downRequester
                            },
                        onClick = { onSelect(item) },
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    meta: SearchSectionMeta,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(meta.accent, meta.secondaryAccent),
                        ),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = meta.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = Color.White,
                )
                if (meta.subtitle.isNotBlank()) {
                    Text(
                        text = meta.subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = Color.White.copy(alpha = 0.46f),
                    )
                }
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.44f),
            modifier = Modifier.size(24.dp),
        )
    }
}

private fun formatHotMetric(hot: Int?): String? {
    if (hot == null || hot <= 0) return null
    return when {
        hot >= 10_000 -> {
            val value = hot / 10_000f
            "↑ ${if (value % 1f == 0f) value.toInt() else String.format("%.1f", value)}万"
        }
        else -> "↑ $hot"
    }
}

@Composable
private fun SearchKeywordPill(
    item: TvSearchKeywordItem,
    meta: SearchSectionMeta,
    index: Int,
    isRanking: Boolean,
    itemWidth: androidx.compose.ui.unit.Dp,
    itemHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape: Shape = RoundedCornerShape(12.dp)
    val rankText = "${index + 1}"
    val trendText = formatHotMetric(item.hot)

    Surface(
        onClick = onClick,
        // 简化了背景处理，直接在 Surface 应用微调底色
        color = if (focused) Color(0xFF1B2945) else Color(0xFF172233),
        shape = shape,
        modifier = modifier
            .consumeRepeatedDpadEvents()
            .width(itemWidth)
            .height(itemHeight)
            .onFocusChanged { focused = it.isFocused }
            // 修复焦点发光问题，使用高亮阴影模拟外发光
            .shadow(
                elevation = if (focused) 8.dp else 0.dp,
                shape = shape,
                ambientColor = if (focused) Color(0x664F98FF) else Color.Transparent,
                spotColor = if (focused) Color(0xCC6FA8FF) else Color.Transparent,
            )
            // 搭配高亮的边框提升精致感
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color(0xFF6EA8FF) else Color.White.copy(alpha = 0.08f),
                shape = shape
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val circleColors = if (isRanking) {
                when (index) {
                    0 -> listOf(Color(0xFFFF5A3C), Color(0xFFFF7D49))
                    1 -> listOf(Color(0xFFFFA72D), Color(0xFFFFB84F))
                    2 -> listOf(Color(0xFFFFD11A), Color(0xFFFFE04A))
                    else -> listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.10f))
                }
            } else {
                listOf(meta.accent.copy(alpha = 0.90f), meta.secondaryAccent.copy(alpha = 0.82f))
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        brush = Brush.linearGradient(colors = circleColors),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isRanking) {
                    Text(
                        text = rankText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        ),
                        color = if (index < 3) Color.White else Color.White.copy(alpha = 0.82f),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color.White, CircleShape),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.word,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = if (isRanking) 15.sp else 14.sp,
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                )
                if (trendText != null) {
                    Text(
                        text = trendText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = if (index < 3) Color(0xFFFF9D6D) else Color.White.copy(alpha = 0.58f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

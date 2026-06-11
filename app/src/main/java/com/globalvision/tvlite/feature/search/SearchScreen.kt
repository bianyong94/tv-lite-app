package com.globalvision.tvlite.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.globalvision.tvlite.core.model.TvPosterItem
import com.globalvision.tvlite.core.network.TvRepository
import com.globalvision.tvlite.feature.common.TvFeedbackPanel
import com.globalvision.tvlite.feature.common.TvLoadingPanel
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
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var searchToken by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<TvPosterItem>>(emptyList()) }
    var totalCount by remember { mutableIntStateOf(0) }
    var nextPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var focusArea by remember { mutableStateOf(SearchFocusArea.SearchField) }
    var pendingFocusFirstResult by remember { mutableStateOf(false) }
    val searchCache = remember { mutableStateMapOf<String, CachedSearchContent>() }

    val searchFieldRequester = remember { FocusRequester() }
    val firstResultRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    fun applyCache(cache: CachedSearchContent) {
        results = cache.items
        totalCount = cache.total
        nextPage = cache.nextPage
        hasMore = cache.hasMore
    }

    fun clearResults() {
        keyword = ""
        results = emptyList()
        totalCount = 0
        nextPage = 1
        hasMore = false
        pendingFocusFirstResult = false
        searchFieldRequester.requestFocus()
        focusArea = SearchFocusArea.SearchField
        statusHost?.show("已清空搜索", "可以重新输入新的关键词。")
    }

    fun triggerSearch(forceRefresh: Boolean = false) {
        val normalized = keyword.trim()
        if (normalized.isBlank()) return
        keyword = normalized
        statusHost?.show("开始搜索", "正在搜索“$normalized”。")

        if (!forceRefresh) {
            searchCache[normalized]?.let { cached ->
                applyCache(cached)
                pendingFocusFirstResult = cached.items.isNotEmpty()
                if (cached.items.isEmpty()) {
                    focusArea = SearchFocusArea.SearchField
                }
                searchToken = 0
                return
            }
        }

        searchToken += 1
        pendingFocusFirstResult = true
    }

    suspend fun loadMoreResults() {
        val normalized = keyword.trim()
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
        searchFieldRequester.requestFocus()
    }

    LaunchedEffect(keyword) {
        val normalized = keyword.trim()
        if (normalized.isBlank()) {
            results = emptyList()
            totalCount = 0
            nextPage = 1
            hasMore = false
            pendingFocusFirstResult = false
            return@LaunchedEffect
        }

        delay(450)
        if (keyword.trim() == normalized) {
            triggerSearch(forceRefresh = true)
        }
    }

    LaunchedEffect(searchToken) {
        if (searchToken == 0) return@LaunchedEffect
        loading = true
        val normalized = keyword.trim()
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
    }

    LaunchedEffect(results, pendingFocusFirstResult) {
        if (pendingFocusFirstResult && results.isNotEmpty()) {
            firstResultRequester.requestFocus()
            focusArea = SearchFocusArea.Results
            pendingFocusFirstResult = false
        }
    }

    LaunchedEffect(gridState, results, hasMore, loading, loadingMore) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
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

    TvScreenScaffold(
        title = "",
        onBack = null,
        showTitle = false,
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy((layout.railSpacing * 0.5f).coerceAtLeast(8.dp)),
        ) {
            val controlHeight = (layout.posterWidth * 0.3f).coerceIn(50.dp, 72.dp)
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                placeholder = { Text("搜索影片") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TvFocusBorder,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                    cursorColor = TvFocusBorder,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(controlHeight)
                    .focusRequester(searchFieldRequester)
                    .onFocusChanged {
                        if (it.isFocused) {
                            focusArea = SearchFocusArea.SearchField
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter, Key.DirectionCenter -> {
                                triggerSearch(forceRefresh = true)
                                true
                            }
                            Key.DirectionDown -> {
                                if (results.isNotEmpty()) {
                                    firstResultRequester.requestFocus()
                                    focusArea = SearchFocusArea.Results
                                    true
                                } else {
                                    false
                                }
                            }
                            else -> false
                        }
                    },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge,
            )

            when {
                loading -> {
                    TvLoadingPanel(
                        message = if (keyword.isBlank()) "正在准备搜索面板..." else "正在搜索“$keyword”...",
                        centered = true,
                    )
                }
                results.isEmpty() && keyword.isBlank() -> Box(modifier = Modifier.weight(1f))
                results.isEmpty() -> {
                    TvFeedbackPanel(
                        title = "没有找到匹配内容",
                        message = "可以尝试更短的片名、别名，或者换一个演员关键字。",
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(6),
                        contentPadding = PaddingValues(bottom = layout.railSpacing),
                        horizontalArrangement = Arrangement.spacedBy((layout.railSpacing * 0.72f).coerceAtLeast(12.dp)),
                        verticalArrangement = Arrangement.spacedBy((layout.railSpacing * 0.72f).coerceAtLeast(12.dp)),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .focusGroup(),
                    ) {
                        itemsIndexed(results, key = { _, item -> item.id }) { index, item ->
                            val isTopRow = index < 6
                            TvPosterCard(
                                item = item,
                                width = layout.posterWidth,
                                modifier = Modifier
                                    .then(
                                        if (index == 0) {
                                            Modifier.focusRequester(firstResultRequester)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .then(
                                        if (isTopRow) {
                                            Modifier.focusProperties { up = searchFieldRequester }
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .onPreviewKeyEvent { event ->
                                        if (
                                            event.type == KeyEventType.KeyDown &&
                                            event.key == Key.DirectionUp &&
                                            index < 6
                                        ) {
                                            searchFieldRequester.requestFocus()
                                            focusArea = SearchFocusArea.SearchField
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                onClick = { onOpenDetail(item.id) },
                            )
                        }

                        if (loadingMore) {
                            item(span = { GridItemSpan(6) }) {
                                TvLoadingPanel(message = "正在加载更多搜索结果...")
                            }
                        } else if (!hasMore && results.isNotEmpty()) {
                            item(span = { GridItemSpan(6) }) {
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

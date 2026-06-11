package com.globalvision.tvlite.feature.home

import android.app.Activity
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.globalvision.tvlite.core.model.TvFilterGroup
import com.globalvision.tvlite.core.model.TvHomeFeed
import com.globalvision.tvlite.core.model.TvNavItem
import com.globalvision.tvlite.core.model.TvPosterItem
import com.globalvision.tvlite.core.network.TvRepository
import com.globalvision.tvlite.feature.common.TvFeedbackPanel
import com.globalvision.tvlite.feature.common.TvLoadingPanel
import com.globalvision.tvlite.feature.common.TvScreenScaffold
import com.globalvision.tvlite.feature.common.LocalTvStatusHostState
import com.globalvision.tvlite.ui.theme.TvFocusBorder
import com.globalvision.tvlite.ui.theme.rememberTvLayoutMetrics
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged

private const val PAGE_SIZE = 30
private val FilterChipHeight = 26.dp
private val FilterChipShape = RoundedCornerShape(FilterChipHeight / 2)

private enum class HomeFocusZone {
    Sidebar,
    RightContent,
}

@Composable
fun HomeScreen(
    repository: TvRepository,
    onSearch: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val layout = rememberTvLayoutMetrics()
    val statusHost = LocalTvStatusHostState.current
    val tag = "HomeScreen"
    var feedState by remember { mutableStateOf<TvHomeFeed?>(null) }
    var loadingFeed by remember { mutableStateOf(true) }
    var activeNavId by rememberSaveable { mutableStateOf(0) }
    val filtersByNavId = remember { mutableStateMapOf<Int, TopicFilters>() }
    val screenCache = remember { mutableStateMapOf<ScreenQueryKey, CachedScreenContent>() }
    var contentItems by remember { mutableStateOf<List<TvPosterItem>>(emptyList()) }
    var contentLoading by remember { mutableStateOf(true) }
    var contentPage by remember { mutableIntStateOf(1) }
    var hasMoreContent by remember { mutableStateOf(true) }
    var loadingMoreContent by remember { mutableStateOf(false) }
    var focusZone by remember { mutableStateOf(HomeFocusZone.Sidebar) }
    var showExitDialog by remember { mutableStateOf(false) }
    var focusedContentIndex by remember { mutableIntStateOf(-1) }

    val context = LocalContext.current
    val searchFocusRequester = remember { FocusRequester() }
    val navFocusRequester = remember { FocusRequester() }
    val filterEntryFocusRequester = remember { FocusRequester() }
    val filterExitFocusRequester = remember { FocusRequester() }
    val gridEntryFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    val navItems = remember(feedState) {
        buildHomeNavItems(feedState?.config?.topNav.orEmpty())
    }
    val activeFilterGroup = remember(feedState, activeNavId) {
        feedState?.config?.filterGroups?.firstOrNull { it.id == activeNavId }
    }
    val topicFilters = if (activeNavId == 0) {
        TopicFilters()
    } else {
        filtersByNavId[activeNavId] ?: TopicFilters(
            sort = activeFilterGroup?.sortValues?.firstOrNull().orEmpty().ifBlank { "by_time" },
        )
    }
    val contentQueryKey = ScreenQueryKey(
        navId = activeNavId,
        sort = topicFilters.sort,
        classValue = topicFilters.classValue,
        area = topicFilters.area,
        year = topicFilters.year,
    )
    fun requestNextContentPage() {
        if (activeNavId == 0 || contentLoading || loadingMoreContent || !hasMoreContent) return

        val pageToLoad = contentPage
        loadingMoreContent = true
        scope.launch {
            val items = try {
                repository.getScreenMovies(
                    typeId = activeNavId,
                    sort = topicFilters.sort.ifBlank { "by_time" },
                    classValue = topicFilters.classValue.ifBlank { null },
                    area = topicFilters.area.ifBlank { null },
                    year = topicFilters.year.ifBlank { null },
                    page = pageToLoad,
                    pageSize = PAGE_SIZE,
                )
            } catch (_: Throwable) {
                statusHost?.show("加载更多失败", "当前网络较慢，稍后再试。", timeoutMs = 2600L)
                emptyList()
            }

            if (items.isNotEmpty()) {
                contentItems = contentItems + items
            } else {
                hasMoreContent = false
            }
            hasMoreContent = hasMoreContent && items.size >= PAGE_SIZE
            contentPage = pageToLoad + 1
            loadingMoreContent = false
            screenCache[contentQueryKey] = CachedScreenContent(
                items = contentItems,
                nextPage = contentPage,
                hasMore = hasMoreContent,
            )
            Log.d(tag, "append screen items: typeId=$activeNavId page=$pageToLoad count=${items.size} total=${contentItems.size}")
        }
    }

    LaunchedEffect(Unit) {
        repository.peekHomeFeed()?.let {
            feedState = it
            loadingFeed = false
            return@LaunchedEffect
        }
        loadingFeed = true
        feedState = try {
            repository.getHomeFeed()
        } catch (_: Throwable) {
            null
        }
        loadingFeed = false
    }

    LaunchedEffect(feedState) {
        val feed = feedState ?: return@LaunchedEffect
        val availableNavIds = navItems.map { it.id }
        if (activeNavId !in availableNavIds) {
            activeNavId = navItems.firstOrNull()?.id ?: 0
        }
        if (activeNavId == 0) {
            focusZone = HomeFocusZone.Sidebar
            scope.launch {
                navFocusRequester.requestFocus()
            }
        }
    }

    LaunchedEffect(feedState, activeNavId, topicFilters) {
        val feed = feedState ?: return@LaunchedEffect
        val nav = navItems.firstOrNull { it.id == activeNavId } ?: navItems.firstOrNull()
        if (nav == null) return@LaunchedEffect

        if (activeNavId == 0) {
            val recommendItems = feed.banners
                .plus(feed.sections.flatMap { it.items })
                .distinctBy { it.id }
                .take(72)
            val recommendState = CachedScreenContent(
                items = recommendItems,
                nextPage = 1,
                hasMore = false,
            )
            screenCache[contentQueryKey] = recommendState
            contentLoading = false
            contentPage = 1
            hasMoreContent = false
            loadingMoreContent = false
            focusedContentIndex = -1
            contentItems = recommendState.items
            scope.launch { gridState.scrollToItem(0) }
            Log.d(tag, "show recommend items: count=${contentItems.size}")
            return@LaunchedEffect
        }

        screenCache[contentQueryKey]?.let { cached ->
            contentItems = cached.items
            contentLoading = false
            loadingMoreContent = false
            contentPage = cached.nextPage
            hasMoreContent = cached.hasMore
            focusedContentIndex = -1
            scope.launch { gridState.scrollToItem(0) }
            Log.d(tag, "screen cache hit: typeId=$activeNavId items=${cached.items.size} nextPage=${cached.nextPage} hasMore=${cached.hasMore}")
            return@LaunchedEffect
        }

        repository.peekScreenMoviesState(
            typeId = activeNavId,
            sort = topicFilters.sort.ifBlank { "by_time" },
            classValue = topicFilters.classValue.ifBlank { null },
            area = topicFilters.area.ifBlank { null },
            year = topicFilters.year.ifBlank { null },
        )?.let { cached ->
            val cachedContent = CachedScreenContent(
                items = cached.items,
                nextPage = cached.nextPage,
                hasMore = cached.hasMore,
            )
            screenCache[contentQueryKey] = cachedContent
            contentItems = cachedContent.items
            contentLoading = false
            loadingMoreContent = false
            contentPage = cachedContent.nextPage
            hasMoreContent = cachedContent.hasMore
            focusedContentIndex = -1
            scope.launch { gridState.scrollToItem(0) }
            Log.d(tag, "repository state cache hit: typeId=$activeNavId items=${cachedContent.items.size} nextPage=${cachedContent.nextPage} hasMore=${cachedContent.hasMore}")
            return@LaunchedEffect
        }

        repository.peekScreenMoviesPage(
            typeId = activeNavId,
            sort = topicFilters.sort.ifBlank { "by_time" },
            classValue = topicFilters.classValue.ifBlank { null },
            area = topicFilters.area.ifBlank { null },
            year = topicFilters.year.ifBlank { null },
            page = 1,
        )?.let { cachedPage1 ->
            val cachedContent = CachedScreenContent(
                items = cachedPage1,
                nextPage = 2,
                hasMore = cachedPage1.size >= PAGE_SIZE,
            )
            screenCache[contentQueryKey] = cachedContent
            contentItems = cachedContent.items
            contentLoading = false
            loadingMoreContent = false
            contentPage = cachedContent.nextPage
            hasMoreContent = cachedContent.hasMore
            focusedContentIndex = -1
            scope.launch { gridState.scrollToItem(0) }
            Log.d(tag, "repository page cache hit: typeId=$activeNavId items=${cachedPage1.size}")
            return@LaunchedEffect
        }

        contentLoading = true
        loadingMoreContent = false
        contentPage = 1
        hasMoreContent = true
        scope.launch { gridState.scrollToItem(0) }

        val items = try {
            repository.getScreenMovies(
                typeId = activeNavId,
                sort = topicFilters.sort.ifBlank { "by_time" },
                classValue = topicFilters.classValue.ifBlank { null },
                area = topicFilters.area.ifBlank { null },
                year = topicFilters.year.ifBlank { null },
                page = 1,
                pageSize = PAGE_SIZE,
            )
        } catch (_: Throwable) {
            statusHost?.show("内容加载失败", "当前栏目暂时无法获取数据。", timeoutMs = 2600L)
            emptyList()
        }
        contentItems = items
        contentLoading = false
        hasMoreContent = items.size >= PAGE_SIZE
        contentPage = 2
        focusedContentIndex = -1
        screenCache[contentQueryKey] = CachedScreenContent(
            items = items,
            nextPage = 2,
            hasMore = hasMoreContent,
        )
        Log.d(tag, "show screen items: typeId=$activeNavId count=${items.size}")
    }

    LaunchedEffect(contentQueryKey, hasMoreContent, contentLoading, loadingMoreContent) {
        if (activeNavId == 0) return@LaunchedEffect

        snapshotFlow {
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalCount = gridState.layoutInfo.totalItemsCount
            lastVisibleIndex to totalCount
        }
            .distinctUntilChanged()
            .collect { (lastVisibleIndex, totalCount) ->
                if (contentLoading || loadingMoreContent || !hasMoreContent || totalCount == 0) return@collect
                val threshold = 12
                if (lastVisibleIndex >= totalCount - threshold) {
                    requestNextContentPage()
                }
            }
    }

    BackHandler(enabled = showExitDialog) {
        showExitDialog = false
    }

    BackHandler(enabled = !showExitDialog) {
        when (focusZone) {
            HomeFocusZone.RightContent -> {
                scope.launch {
                    navFocusRequester.requestFocus()
                }
            }
            HomeFocusZone.Sidebar -> {
                showExitDialog = true
            }
        }
    }

    if (showExitDialog) {
        ExitConfirmDialog(
            onDismiss = { showExitDialog = false },
            onConfirm = {
                showExitDialog = false
                (context as? Activity)?.finish()
            },
        )
    }

    if (loadingFeed && feedState == null) {
        TvScreenScaffold(
            title = "",
            showTitle = false,
        ) {
            TvLoadingPanel(
                message = "正在准备首页栏目与推荐内容...",
                centered = true,
            )
        }
        return
    }

    val feed = feedState
    if (feed == null) {
        TvScreenScaffold(
            title = "",
            showTitle = false,
        ) {
            TvFeedbackPanel(
                title = "首页数据加载失败",
                message = "当前无法获取首页内容，你可以先尝试进入搜索，或稍后重新打开应用。",
                action = { SearchAction(onSearch, searchFocusRequester) },
            )
        }
        return
    }

    TvScreenScaffold(
        title = "",
        showTitle = false,
        contentPadding = PaddingValues(horizontal = layout.railSpacing / 2, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .width((layout.posterWidth * 0.55f).coerceAtLeast(96.dp))
                    .fillMaxHeight()
                    .padding(top = 4.dp)
                    .focusGroup(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SearchAction(
                    onSearch = onSearch,
                    focusRequester = searchFocusRequester,
                    onFocused = { focusZone = HomeFocusZone.Sidebar },
                )
                Text(
                    text = "分类",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 2.dp, bottom = 0.dp),
                )
                navItems.forEachIndexed { index, item ->
                    val rightTarget = if (item.id == 0) {
                        filterEntryFocusRequester
                    } else {
                        gridEntryFocusRequester
                    }
                    SidebarTab(
                        text = item.name,
                        selected = item.id == activeNavId,
                        modifier = if (index == 0) {
                            Modifier.focusRequester(navFocusRequester)
                        } else {
                            Modifier
                        },
                        rightFocusRequester = rightTarget,
                        onFocused = { focusZone = HomeFocusZone.Sidebar },
                        onClick = {
                            Log.d(tag, "select nav: id=${item.id} name=${item.name}")
                            activeNavId = item.id
                            statusHost?.show("切换栏目", "正在查看 ${item.name}")
                        },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy((layout.railSpacing * 0.75f).coerceAtLeast(12.dp)),
            ) {
                if (activeNavId > 0 && activeFilterGroup != null) {
                    FilterPanel(
                        filterGroup = activeFilterGroup,
                        filters = topicFilters,
                        entryFocusRequester = filterEntryFocusRequester,
                        exitFocusRequester = filterExitFocusRequester,
                        gridEntryFocusRequester = gridEntryFocusRequester,
                        onFocused = { focusZone = HomeFocusZone.RightContent },
                        onChange = { next ->
                            filtersByNavId[activeNavId] = next
                            Log.d(
                                tag,
                                "select filters: typeId=$activeNavId sort=${next.sort} class=${next.classValue} area=${next.area} year=${next.year}",
                            )
                            val summary = listOf(
                                next.classValue.ifBlank { "全部类型" },
                                next.area.ifBlank { "全部地区" },
                                next.year.ifBlank { "全部年份" },
                            ).joinToString(" · ")
                            statusHost?.show("筛选已更新", summary)
                        },
                    )
                }

                if (contentLoading && contentItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (!contentLoading && contentItems.isEmpty()) {
                    TvFeedbackPanel(
                        title = if (activeNavId == 0) "暂无推荐内容" else "当前筛选下没有内容",
                        message = if (activeNavId == 0) {
                            "首页推荐暂时为空，可以切换左侧栏目继续浏览。"
                        } else {
                            "可以调整分类、地区、年份或排序条件后重试。"
                        },
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        horizontalArrangement = Arrangement.spacedBy((layout.railSpacing * 0.72f).coerceAtLeast(12.dp)),
                        verticalArrangement = Arrangement.spacedBy((layout.railSpacing * 0.72f).coerceAtLeast(12.dp)),
                        contentPadding = PaddingValues(bottom = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onPreviewKeyEvent { event ->
                                if (
                                    event.type == KeyEventType.KeyDown &&
                                    event.key == Key.DirectionDown &&
                                    focusedContentIndex >= maxOf(contentItems.size - 6, 0)
                                ) {
                                    requestNextContentPage()
                                    true
                                } else {
                                    false
                                }
                            }
                            .focusGroup(),
                        state = gridState,
                    ) {
                        itemsIndexed(contentItems, key = { index, item -> item.id }) { index, item ->
                            val gridTopUpTarget = if (activeNavId == 0) navFocusRequester else filterExitFocusRequester
                            CompactPosterCard(
                                item = item,
                                modifier = if (index == 0) {
                                    Modifier
                                        .focusRequester(if (activeNavId == 0) filterEntryFocusRequester else gridEntryFocusRequester)
                                        .focusProperties { up = gridTopUpTarget }
                                } else if (index < 6) {
                                    Modifier.focusProperties { up = gridTopUpTarget }
                                } else {
                                    Modifier
                                },
                                onFocused = {
                                    focusZone = HomeFocusZone.RightContent
                                    focusedContentIndex = index
                                },
                                onClick = { onOpenDetail(item.id) },
                            )
                        }
                        if (loadingMoreContent) {
                            item(span = { GridItemSpan(6) }) {
                                TvLoadingPanel(message = "正在加载更多内容...")
                            }
                        } else if (!hasMoreContent && contentItems.isNotEmpty()) {
                            item(span = { GridItemSpan(6) }) {
                                TvFeedbackPanel(
                                    title = "内容已经到底",
                                    message = "当前栏目内容已全部加载完成，可以切换分类或筛选继续浏览。",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class TopicFilters(
    val sort: String = "by_time",
    val classValue: String = "",
    val area: String = "",
    val year: String = "",
)

private data class ScreenQueryKey(
    val navId: Int,
    val sort: String,
    val classValue: String,
    val area: String,
    val year: String,
)

private data class CachedScreenContent(
    val items: List<TvPosterItem>,
    val nextPage: Int,
    val hasMore: Boolean,
)

private data class FilterOption(
    val label: String,
    val value: String,
)

@Composable
private fun ExitConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("退出应用") },
        text = { Text("确定要退出应用吗？") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("是")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("否")
            }
        },
    )
}

@Composable
private fun SearchAction(
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
    val layout = rememberTvLayoutMetrics()
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val buttonSize = (layout.posterWidth * 0.24f).coerceIn(44.dp, 64.dp)
    val iconSize = (buttonSize * 0.5f).coerceIn(20.dp, 28.dp)
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 420f),
        label = "home_search_scale",
    )

    Card(
        onClick = onSearch,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = shape,
        modifier = modifier
            .size(buttonSize)
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) TvFocusBorder else Color.Transparent,
                shape = shape,
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "搜索",
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
private fun FilterPanel(
    filterGroup: TvFilterGroup,
    filters: TopicFilters,
    entryFocusRequester: FocusRequester,
    exitFocusRequester: FocusRequester,
    gridEntryFocusRequester: FocusRequester,
    onChange: (TopicFilters) -> Unit,
    onFocused: () -> Unit = {},
) {
    val layout = rememberTvLayoutMetrics()
    Column(verticalArrangement = Arrangement.spacedBy((layout.railSpacing * 0.4f).coerceAtLeast(6.dp))) {
        FilterRow(
            label = "综合",
            items = buildSortFilterOptions(filterGroup.sortValues.ifEmpty { listOf("by_time", "by_hits", "by_score") }),
            selected = filters.sort,
            showAll = false,
            firstItemFocusRequester = entryFocusRequester,
            onFocused = onFocused,
            onSelect = { selected -> onChange(filters.copy(sort = selected)) },
        )
        if (filterGroup.classValues.drop(1).isNotEmpty()) {
            FilterRow(
                label = "类型",
                items = filterGroup.classValues.drop(1).map { FilterOption(label = it, value = it) },
                selected = filters.classValue,
                onFocused = onFocused,
                onSelect = { selected -> onChange(filters.copy(classValue = selected)) },
            )
        }
        if (filterGroup.areaValues.drop(1).isNotEmpty()) {
            FilterRow(
                label = "地区",
                items = filterGroup.areaValues.drop(1).map { FilterOption(label = it, value = it) },
                selected = filters.area,
                onFocused = onFocused,
                onSelect = { selected -> onChange(filters.copy(area = selected)) },
            )
        }
        if (filterGroup.yearValues.drop(1).isNotEmpty()) {
            FilterRow(
                label = "年份",
                items = filterGroup.yearValues.drop(1).map { FilterOption(label = it, value = it) },
                selected = filters.year,
                firstItemFocusRequester = exitFocusRequester,
                rowExitFocusRequester = gridEntryFocusRequester,
                onFocused = onFocused,
                onSelect = { selected -> onChange(filters.copy(year = selected)) },
            )
        }
    }
}

@Composable
private fun FilterRow(
    label: String,
    items: List<FilterOption>,
    selected: String,
    onSelect: (String) -> Unit,
    showAll: Boolean = true,
    firstItemFocusRequester: FocusRequester? = null,
    rowExitFocusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
) {
    val layout = rememberTvLayoutMetrics()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width((layout.posterWidth * 0.22f).coerceIn(40.dp, 64.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            FilterLabelChip(text = label)
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy((layout.railSpacing * 0.4f).coerceAtLeast(6.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showAll) {
                FilterItemChip(
                    text = "全部",
                    selected = selected.isBlank(),
                    onFocused = onFocused,
                    onClick = { onSelect("") },
                )
            }
            items.forEach { option ->
                FilterItemChip(
                    text = option.label,
                    selected = selected == option.value,
                    modifier = if (firstItemFocusRequester != null && option == items.firstOrNull()) {
                        Modifier
                            .focusRequester(firstItemFocusRequester)
                            .then(if (rowExitFocusRequester != null) Modifier.focusProperties { down = rowExitFocusRequester } else Modifier)
                    } else {
                        Modifier
                    },
                    onFocused = onFocused,
                    onClick = { onSelect(option.value) },
                )
            }
        }
    }
}

@Composable
private fun FilterLabelChip(text: String) {
    val layout = rememberTvLayoutMetrics()
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        shape = FilterChipShape,
        modifier = Modifier.height((layout.railSpacing * 1.3f).coerceIn(FilterChipHeight, 38.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FilterItemChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    downFocusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
) {
    val layout = rememberTvLayoutMetrics()
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = when {
        !focused -> Color.Transparent
        else -> TvFocusBorder
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    LaunchedEffect(focused) {
        if (focused) onFocused()
    }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "filter_chip_scale",
    )

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = if (focused && !selected) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            } else {
                containerColor
            },
            contentColor = contentColor,
        ),
        shape = FilterChipShape,
        modifier = modifier
            .height((layout.railSpacing * 1.3f).coerceIn(FilterChipHeight, 38.dp))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = borderColor,
                shape = FilterChipShape,
            )
            .onPreviewKeyEvent { event ->
                if (downFocusRequester != null && event.type == KeyEventType.KeyUp && event.key == Key.DirectionDown) {
                    downFocusRequester.requestFocus()
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SidebarTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    rightFocusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
) {
    val layout = rememberTvLayoutMetrics()
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
        label = "sidebar_tab_scale",
    )

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
            contentColor = contentColor,
        ),
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .height((layout.posterWidth * 0.22f).coerceIn(40.dp, 64.dp))
            .onPreviewKeyEvent { event ->
                if (rightFocusRequester != null && event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                    rightFocusRequester.requestFocus()
                    true
                } else {
                    false
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) TvFocusBorder else Color.Transparent,
                shape = shape,
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CompactPosterCard(
    item: TvPosterItem,
    modifier: Modifier = Modifier,
    upFocusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
        label = "compact_poster_scale",
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                if (upFocusRequester != null && event.type == KeyEventType.KeyUp && event.key == Key.DirectionUp) {
                    upFocusRequester.requestFocus()
                    true
                } else {
                    false
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) TvFocusBorder else Color.Transparent,
                shape = shape,
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = shape,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(shape),
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
                                colors = listOf(
                                    Color.Transparent,
                                    if (focused) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    } else {
                                        Color.Transparent
                                    },
                                ),
                            ),
                        ),
                )
                if (item.remark.isNotBlank()) {
                    Text(
                        text = item.remark,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .padding(8.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
                                RoundedCornerShape(999.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
                maxLines = 1,
            )
            if (item.year.isNotBlank() || item.category.isNotBlank()) {
                Text(
                    text = listOf(item.year, item.category).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    maxLines = 1,
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

private fun buildHomeNavItems(items: List<TvNavItem>): List<TvNavItem> {
    if (items.isEmpty()) {
        return listOf(
            TvNavItem(id = 0, name = "推荐"),
            TvNavItem(id = 1, name = "电影"),
            TvNavItem(id = 2, name = "剧集"),
            TvNavItem(id = 3, name = "综艺"),
        )
    }

    val result = items.filter { it.id > 0 && it.name != "推荐" }.toMutableList()
    if (result.none { it.id == 0 }) {
        result.add(0, TvNavItem(id = 0, name = "推荐"))
    }
    return result
}

private fun buildSortFilterOptions(values: List<String>): List<FilterOption> {
    return values.map { value ->
        val label = when (value) {
            "by_time" -> "最新"
            "by_hits" -> "最热"
            "by_score" -> "评分"
            else -> value
        }
        FilterOption(label = label, value = value)
    }
}

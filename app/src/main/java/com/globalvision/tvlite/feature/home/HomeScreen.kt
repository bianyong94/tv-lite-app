package com.globalvision.tvlite.feature.home

import android.app.Activity
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.globalvision.tvlite.core.model.TvFilterGroup
import com.globalvision.tvlite.core.model.TvHomeFeed
import com.globalvision.tvlite.core.model.TvNavItem
import com.globalvision.tvlite.core.model.TvPosterItem
import com.globalvision.tvlite.core.network.TvRepository
import com.globalvision.tvlite.core.network.TvRepository.HomeFeedDiagnostic
import com.globalvision.tvlite.feature.common.consumeRepeatedDpadEvents
import com.globalvision.tvlite.feature.common.isRepeatedDpadEvent
import com.globalvision.tvlite.feature.common.TvFeedbackPanel
import com.globalvision.tvlite.feature.common.TvLoadingPanel
import com.globalvision.tvlite.feature.common.TvScreenScaffold
import com.globalvision.tvlite.feature.common.LocalTvStatusHostState
import com.globalvision.tvlite.ui.theme.rememberTvLayoutMetrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged

private const val PAGE_SIZE = 30
// 调大 Filter 芯片高度，使其在大屏更有存在感
private val FilterChipHeight = 32.dp
private val FilterChipShape = RoundedCornerShape(10.dp) // Apple 偏向富有几何感的圆角

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
    var feedDiagnostic by remember { mutableStateOf<HomeFeedDiagnostic?>(null) }
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
        }
    }

    LaunchedEffect(Unit) {
        repository.peekHomeFeed()?.let {
            feedState = it
            feedDiagnostic = repository.peekHomeFeedDiagnostic()
            loadingFeed = false
            return@LaunchedEffect
        }
        loadingFeed = true
        feedState = try {
            repository.getHomeFeed()
        } catch (_: Throwable) {
            null
        }
        feedDiagnostic = repository.peekHomeFeedDiagnostic()
        loadingFeed = false
    }

    LaunchedEffect(feedState) {
        val availableNavIds = navItems.map { it.id }
        if (activeNavId !in availableNavIds) {
            activeNavId = navItems.firstOrNull()?.id ?: 0
        }
        if (activeNavId == 0) {
            focusZone = HomeFocusZone.Sidebar
            scope.launch {
                delay(32)
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
            contentItems = recommendState.items
            scope.launch { gridState.scrollToItem(0) }
            return@LaunchedEffect
        }

        screenCache[contentQueryKey]?.let { cached ->
            contentItems = cached.items
            contentLoading = false
            loadingMoreContent = false
            contentPage = cached.nextPage
            hasMoreContent = cached.hasMore
            scope.launch { gridState.scrollToItem(0) }
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
        screenCache[contentQueryKey] = CachedScreenContent(
            items = items,
            nextPage = 2,
            hasMore = hasMoreContent,
        )
    }

    LaunchedEffect(contentQueryKey, hasMoreContent, contentLoading, loadingMoreContent) {
        if (activeNavId == 0) return@LaunchedEffect

        snapshotFlow {
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalCount = gridState.layoutInfo.totalItemsCount
            lastVisibleIndex to totalCount
        }.distinctUntilChanged()
            .collect { (lastVisibleIndex, totalCount) ->
                if (contentLoading || loadingMoreContent || !hasMoreContent || totalCount == 0) return@collect
                val threshold = 12
                if (lastVisibleIndex >= totalCount - threshold) {
                    requestNextContentPage()
                }
            }
    }

    BackHandler(enabled = showExitDialog) { showExitDialog = false }

    BackHandler(enabled = !showExitDialog) {
        when (focusZone) {
            HomeFocusZone.RightContent -> {
                scope.launch {
                    delay(32)
                    navFocusRequester.requestFocus()
                }
            }
            HomeFocusZone.Sidebar -> { showExitDialog = true }
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
        TvScreenScaffold(title = "", showTitle = false) {
            TvLoadingPanel(message = "正在准备首页栏目与推荐内容...", centered = true)
        }
        return
    }

    val feed = feedState
    if (feed == null) {
        TvScreenScaffold(title = "", showTitle = false) {
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
        contentPadding = PaddingValues(0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            // ================= 左侧导航栏组件 =================
            Column(
                modifier = Modifier
                    .width(130.dp) // 适度加宽，承载更大字体的导航项
                    .fillMaxHeight()
                    .focusGroup(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SearchAction(
                    onSearch = onSearch,
                    focusRequester = searchFocusRequester,
                    onFocused = { focusZone = HomeFocusZone.Sidebar },
                )
                
               
                
                navItems.forEachIndexed { index, item ->
                    val rightTarget = if (item.id == 0) filterEntryFocusRequester else gridEntryFocusRequester
                    SidebarTab(
                        text = item.name,
                        selected = item.id == activeNavId,
                        modifier = if (index == 0) Modifier.focusRequester(navFocusRequester) else Modifier,
                        rightFocusRequester = rightTarget,
                        onFocused = { focusZone = HomeFocusZone.Sidebar },
                        onClick = {
                            activeNavId = item.id
                            statusHost?.show("切换栏目", "正在查看 ${item.name}")
                        },
                    )
                }
            }

            // ================= 右侧内容主区域 =================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 4.dp)
                    }
                } else if (!contentLoading && contentItems.isEmpty()) {
                    TvFeedbackPanel(
                        title = if (activeNavId == 0) "暂无推荐内容" else "当前筛选下没有内容",
                        message = "可以调整分类、地区、年份或排序条件后重试。",
                    )
                } else {
                    // 核心网格布局
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5), // 从 6 列改为 5 列，使海报在 4K 上更大、更有表现力
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .focusGroup(),
                        state = gridState,
                    ) {
                        itemsIndexed(contentItems, key = { _, item -> item.id }) { index, item ->
                            val gridTopUpTarget = if (activeNavId == 0) navFocusRequester else filterExitFocusRequester
                            CompactPosterCard(
                                item = item,
                                modifier = when {
                                    index == 0 -> Modifier
                                        .focusRequester(if (activeNavId == 0) filterEntryFocusRequester else gridEntryFocusRequester)
                                        .focusProperties { up = gridTopUpTarget }
                                    index < 5 -> Modifier.focusProperties { up = gridTopUpTarget }
                                    else -> Modifier
                                },
                                onFocused = {
                                    focusZone = HomeFocusZone.RightContent
                                },
                                onClick = { onOpenDetail(item.id) },
                            )
                        }
                        if (loadingMoreContent) {
                            item(span = { GridItemSpan(5) }) {
                                TvLoadingPanel(message = "正在加载更多内容...")
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
private fun ExitConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("退出应用", style = MaterialTheme.typography.titleLarge) },
        text = { Text("确定要退出应用吗？", style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { Button(onClick = onConfirm) { Text("是") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("否") } },
    )
}

// ================= 样式重构与细节升级组件 =================

@Composable
private fun SearchAction(
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    
    // 放大悬浮动效比例，迎合 Apple TV 沉浸反馈
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 380f),
        label = "home_search_scale",
    )

    Card(
        onClick = onSearch,
        colors = CardDefaults.cardColors(
            containerColor = if (focused) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            contentColor = if (focused) Color.Black else Color.White,
        ),
        shape = shape,
        modifier = modifier
            .consumeRepeatedDpadEvents()
            .size(50.dp)
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .shadow(
                elevation = if (focused) 8.dp else 0.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.4f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "搜索",
                modifier = Modifier.size(26.dp),
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
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    
    // Apple TV 标志性的呼吸级微缩放
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "sidebar_tab_scale",
    )

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = when {
                focused -> Color.White
                selected -> Color.White.copy(alpha = 0.22f)
                else -> Color.Transparent // 默认透明非获焦背景，避免过于杂乱
            },
            contentColor = when {
                focused -> Color.Black
                selected -> Color.White
                else -> Color.White.copy(alpha = 0.55f)
            },
        ),
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .consumeRepeatedDpadEvents()
            .focusProperties {
                rightFocusRequester?.let { right = it }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            Text(
                text = text,
                // 全局调大 4K 上的字体
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 17.sp 
                ),
                modifier = Modifier.padding(start = 16.dp),
                maxLines = 1,
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
    // 摒弃杂乱组合，对筛选容器进行统一约束
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 增加分类标签的前缀辨识度
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.4f)
            ),
            modifier = Modifier.width(56.dp)
        )
        
        // 关键改动：将原本基础 Row 的横向滚动重构为 LazyRow，完美解决横向遥控获焦卡顿与溢出电视屏幕的问题
        LazyRow(
            modifier = Modifier
                .weight(1f)
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
        ) {
            if (showAll) {
                item {
                    FilterItemChip(
                        text = "全部",
                        selected = selected.isBlank(),
                        onFocused = onFocused,
                        onClick = { onSelect("") },
                    )
                }
            }
            items(items) { option ->
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
private fun FilterItemChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(focused) {
        if (focused) onFocused()
    }
    
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "filter_chip_scale",
    )

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = when {
                focused -> Color.White
                selected -> MaterialTheme.colorScheme.primary
                else -> Color.White.copy(alpha = 0.08f) // 类似 Apple TV 的不透明半透硅胶质感
            },
            contentColor = when {
                focused -> Color.Black
                selected -> MaterialTheme.colorScheme.onPrimary
                else -> Color.White.copy(alpha = 0.8f)
            },
        ),
        shape = FilterChipShape,
        modifier = modifier
            .consumeRepeatedDpadEvents()
            .height(FilterChipHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (focused) 4.dp else 0.dp,
                shape = FilterChipShape,
                ambientColor = Color.Black
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Normal
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CompactPosterCard(
    item: TvPosterItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp) // 更圆润的外观
    
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
        label = "compact_poster_scale",
    )

    Column(
        modifier = modifier
            .consumeRepeatedDpadEvents()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .padding(2.dp)
    ) {
        // 海报容器
        Card(
            onClick = onClick,
            shape = shape,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.82f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F1F)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = item.posterUrl.takeIf { it.isNotBlank() },
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                
                // 遮罩渐变层增加深邃度
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.78f)
                                ),
                                startY = 260f
                            )
                        ),
                )
                
                // 左上角的高亮标签角标
                if (item.remark.isNotBlank()) {
                    val remarkBackground = if (focused) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.65f)
                    val remarkTextColor = if (focused) MaterialTheme.colorScheme.onPrimary else Color.White
                    Text(
                        text = item.remark,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = remarkTextColor,
                        modifier = Modifier
                            .padding(8.dp)
                            .background(
                                color = remarkBackground,
                                shape = RoundedCornerShape(6.dp)
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
                                color = Color.White.copy(alpha = 0.72f),
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

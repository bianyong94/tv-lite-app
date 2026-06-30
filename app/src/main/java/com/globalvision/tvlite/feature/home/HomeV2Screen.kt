package com.globalvision.tvlite.feature.home

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.globalvision.tvlite.core.model.TvFilterGroup
import com.globalvision.tvlite.core.model.TvHomeFeed
import com.globalvision.tvlite.core.model.TvHomeSection
import com.globalvision.tvlite.core.model.TvNavItem
import com.globalvision.tvlite.core.model.TvPosterItem
import com.globalvision.tvlite.core.network.TvRepository
import com.globalvision.tvlite.feature.common.LocalTvStatusHostState
import com.globalvision.tvlite.feature.common.TvFeedbackPanel
import com.globalvision.tvlite.feature.common.TvLoadingPanel
import com.globalvision.tvlite.feature.common.consumeRepeatedDpadEvents
import com.globalvision.tvlite.feature.common.tvFocusBorder
import com.globalvision.tvlite.ui.theme.TvDivider
import com.globalvision.tvlite.ui.theme.TvV2FocusGlow
import com.globalvision.tvlite.ui.theme.TvV2MaterialSurface
import com.globalvision.tvlite.ui.theme.TvV2MaterialSurfaceFocused
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val V2_PAGE_SIZE = 30
private const val V2_HERO_RAIL_VISIBLE_LIMIT = 8
private const val V2_SHELF_VISIBLE_LIMIT = 8
private val V2ScreenHorizontalPadding = 64.dp
private val V2CardBleedPadding = 8.dp

private enum class HomeV2FocusLayer {
    TopBar,
    Filters, // 新增 Filter 层
    HeroRail,
    Shelf,
}

@Composable
fun HomeV2Screen(
    repository: TvRepository,
    onSearch: () -> Unit,
    onHistory: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onBackToLegacy: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val statusHost = LocalTvStatusHostState.current
    val scope = rememberCoroutineScope()
    var feedState by remember { mutableStateOf<TvHomeFeed?>(null) }
    var loadingFeed by remember { mutableStateOf(true) }
    var activeNavId by rememberSaveable { mutableIntStateOf(0) }
    var contentItems by remember { mutableStateOf<List<TvPosterItem>>(emptyList()) }
    var contentLoading by remember { mutableStateOf(false) }
    var contentPage by remember { mutableIntStateOf(1) }
    var hasMoreContent by remember { mutableStateOf(true) }
    var loadingMoreContent by remember { mutableStateOf(false) }
    var focusLayer by remember { mutableStateOf(HomeV2FocusLayer.HeroRail) }
    var previewBackdropItem by remember { mutableStateOf<TvPosterItem?>(null) }
    var showFilterOverlay by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    val filtersByNavId = remember { mutableStateMapOf<Int, HomeV2TopicFilters>() }
    val screenCache = remember { mutableStateMapOf<HomeV2ScreenQueryKey, HomeV2CachedScreenContent>() }

    // 焦点管理
    val tabFocusRequester = remember { FocusRequester() }
    val filtersFocusRequester = remember { FocusRequester() }
    val heroRailFocusRequester = remember { FocusRequester() }
    val firstShelfFocusRequester = remember { FocusRequester() }
    val heroRailListState = rememberLazyListState()

    val navItems = remember(feedState) {
        buildHomeV2NavItems(feedState?.config?.topNav.orEmpty())
    }
    val activeFilterGroup = remember(feedState, activeNavId) {
        feedState?.config?.filterGroups?.firstOrNull { it.id == activeNavId }
    }
    val activeNav = navItems.firstOrNull { it.id == activeNavId } ?: navItems.firstOrNull()
    val topicFilters = if (activeNavId == 0) {
        HomeV2TopicFilters()
    } else {
        filtersByNavId[activeNavId] ?: HomeV2TopicFilters(
            sort = activeFilterGroup?.sortValues?.firstOrNull().orEmpty().ifBlank { "by_default" },
        )
    }
    val contentQueryKey = HomeV2ScreenQueryKey(
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
            val result = try {
                repository.getScreenMovies(
                    typeId = activeNavId,
                    sort = topicFilters.sort.ifBlank { "by_default" },
                    classValue = topicFilters.classValue.ifBlank { null },
                    area = topicFilters.area.ifBlank { null },
                    year = topicFilters.year.ifBlank { null },
                    page = pageToLoad,
                    pageSize = V2_PAGE_SIZE,
                )
            } catch (_: Throwable) {
                statusHost?.show("加载更多失败", "当前网络较慢，稍后再试。", timeoutMs = 2600L)
                TvRepository.ScreenMoviesResult(items = emptyList(), nextPage = pageToLoad, hasMore = true, total = 0)
            }
            val nextItems = if (result.items.isNotEmpty()) contentItems + result.items else contentItems
            contentItems = nextItems
            hasMoreContent = result.items.isNotEmpty() && result.hasMore
            contentPage = result.nextPage
            loadingMoreContent = false
            screenCache[contentQueryKey] = HomeV2CachedScreenContent(nextItems, contentPage, hasMoreContent)
        }
    }

    LaunchedEffect(Unit) {
        repository.peekHomeFeed()?.let {
            feedState = it
            loadingFeed = false
            return@LaunchedEffect
        }
        loadingFeed = true
        feedState = try { repository.getHomeFeed() } catch (_: Throwable) { null }
        loadingFeed = false
    }

    LaunchedEffect(feedState) {
        val availableNavIds = navItems.map { it.id }
        if (activeNavId !in availableNavIds) {
            activeNavId = navItems.firstOrNull()?.id ?: 0
        }
    }

    LaunchedEffect(feedState, activeNavId, topicFilters) {
        val feed = feedState ?: return@LaunchedEffect
        if (activeNavId == 0) {
            val recommendItems = feed.banners.plus(feed.sections.flatMap { it.items }).distinctBy { it.id }
            contentItems = recommendItems
            contentLoading = false
            contentPage = 1
            hasMoreContent = false
            loadingMoreContent = false
            screenCache[contentQueryKey] = HomeV2CachedScreenContent(recommendItems, 1, false)
            return@LaunchedEffect
        }

        screenCache[contentQueryKey]?.let { cached ->
            contentItems = cached.items
            contentLoading = false
            contentPage = cached.nextPage
            hasMoreContent = cached.hasMore
            loadingMoreContent = false
            return@LaunchedEffect
        }

        contentLoading = true
        loadingMoreContent = false
        contentPage = 1
        hasMoreContent = true
        val result = try {
            repository.getScreenMovies(
                typeId = activeNavId,
                sort = topicFilters.sort.ifBlank { "by_default" },
                classValue = topicFilters.classValue.ifBlank { null },
                area = topicFilters.area.ifBlank { null },
                year = topicFilters.year.ifBlank { null },
                page = 1,
                pageSize = V2_PAGE_SIZE,
            )
        } catch (_: Throwable) {
            statusHost?.show("内容加载失败", "当前栏目暂时无法获取数据。", timeoutMs = 2600L)
            TvRepository.ScreenMoviesResult(emptyList(), 1, false, 0)
        }
        contentItems = result.items
        contentLoading = false
        hasMoreContent = result.hasMore
        contentPage = result.nextPage
        screenCache[contentQueryKey] = HomeV2CachedScreenContent(result.items, result.nextPage, result.hasMore)
    }

    val feed = feedState
    val isCategoryPage = activeNavId != 0
    val showFilters = isCategoryPage && activeFilterGroup != null
    // 分类页去掉重复的 HeroRail
    val heroRailItems = if (feed == null) emptyList() else buildHomeV2HeroRailItems(feed, activeNavId)
    val hasHeroRail = heroRailItems.isNotEmpty()
    val heroItem = if (feed == null) null else buildHomeV2HeroItem(feed, activeNavId, contentItems)
    
    // 只给“推荐”页渲染 Shelf，分类页改用 Grid 呈现避免资源重复
    val shelves = if (feed == null || isCategoryPage) emptyList() else buildHomeV2Shelves(feed, contentItems)

    val targetDownFocusFromTop = remember(showFilters, hasHeroRail) {
        if (showFilters) filtersFocusRequester
        else if (hasHeroRail) heroRailFocusRequester
        else firstShelfFocusRequester
    }

    val upFocusForContent = remember(hasHeroRail, showFilters) {
        if (hasHeroRail) heroRailFocusRequester
        else if (showFilters) filtersFocusRequester
        else tabFocusRequester
    }

    LaunchedEffect(activeNavId, heroItem?.id) {
        previewBackdropItem = heroItem
    }

    LaunchedEffect(activeNavId, heroRailItems.firstOrNull()?.id, shelves.firstOrNull()?.title) {
        delay(80)
        if (hasHeroRail) {
            heroRailListState.scrollToItem(0)
            heroRailFocusRequester.requestFocus()
        } else if (showFilters) {
            filtersFocusRequester.requestFocus()
        } else {
            firstShelfFocusRequester.requestFocus()
        }
    }

    BackHandler(enabled = showExitDialog) {
        showExitDialog = false
    }
    BackHandler(enabled = !showExitDialog && !showFilterOverlay) {
        when (focusLayer) {
            HomeV2FocusLayer.Shelf -> scope.launch {
                delay(32)
                upFocusForContent.requestFocus()
            }
            HomeV2FocusLayer.HeroRail -> scope.launch {
                delay(32)
                if (showFilters) filtersFocusRequester.requestFocus()
                else tabFocusRequester.requestFocus()
            }
            HomeV2FocusLayer.Filters -> scope.launch {
                delay(32)
                tabFocusRequester.requestFocus()
            }
            HomeV2FocusLayer.TopBar -> {
                if (onBackToLegacy != null) {
                    onBackToLegacy()
                } else {
                    showExitDialog = true
                }
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("退出应用", style = MaterialTheme.typography.titleLarge) },
            text = { Text("确定要退出应用吗？", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        (context as? Activity)?.finish()
                    },
                ) { Text("是") }
            },
            dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("否") } },
        )
    }

    if (showFilterOverlay && activeFilterGroup != null) {
        HomeV2FilterOverlay(
            filterGroup = activeFilterGroup,
            filters = topicFilters,
            onDismiss = { showFilterOverlay = false },
            onChange = { next ->
                filtersByNavId[activeNavId] = next
                val summary = listOf(
                    next.classValue.ifBlank { "全部类型" },
                    next.area.ifBlank { "全部地区" },
                    next.year.ifBlank { "全部年份" },
                    sortLabel(next.sort),
                ).joinToString(" · ")
                statusHost?.show("筛选已更新", summary)
            },
        )
    }

    when {
        loadingFeed && feedState == null -> {
            HomeV2Shell { TvLoadingPanel(message = "正在准备新版首页...", centered = true) }
            return
        }
        feedState == null -> {
            HomeV2Shell {
                TvFeedbackPanel(
                    title = "新版首页数据加载失败",
                    message = "当前无法获取首页内容，你可以先进入搜索或稍后重试。",
                    action = {
                        HomeV2ActionChip(
                            text = "搜索",
                            icon = { Icon(Icons.Default.Search, contentDescription = null) },
                            onClick = onSearch,
                        )
                    },
                )
            }
            return
        }
    }

    HomeV2Shell(backdropItem = previewBackdropItem ?: heroItem) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(start = V2ScreenHorizontalPadding, top = 20.dp, end = V2ScreenHorizontalPadding, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HomeV2TopBar(
                navItems = navItems,
                activeNavId = activeNavId,
                firstTabFocusRequester = tabFocusRequester,
                targetDownFocusRequester = targetDownFocusFromTop,
                onTabFocus = { focusLayer = HomeV2FocusLayer.TopBar },
                onSelectNav = { item ->
                    activeNavId = item.id
                    statusHost?.show("切换栏目", "正在查看 ${item.name}", timeoutMs = 1400L)
                },
                onSearch = onSearch,
                onHistory = onHistory,
                onBackToLegacy = onBackToLegacy,
            )

            Box(modifier = Modifier.weight(1f)) {
                when {
                    contentLoading && contentItems.isEmpty() -> {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 4.dp,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    !contentLoading && shelves.isEmpty() && contentItems.isEmpty() -> {
                        TvFeedbackPanel(
                            title = "当前没有可展示内容",
                            message = "可以切换栏目或调整筛选条件后重试。",
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .focusGroup(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 52.dp, top = 2.dp),
                        ) {
                            if (heroItem != null) {
                                item(key = "hero") {
                                    HomeV2Hero(item = heroItem)
                                }
                            }

                            if (showFilters) {
                                item(key = "filters") {
                                    HomeV2FilterSummaryRow(
                                        filters = topicFilters,
                                        hasAdvancedFilters = activeFilterGroup!!.hasAdvancedFilters(),
                                        modifier = Modifier
                                            .focusRequester(filtersFocusRequester)
                                            .focusProperties {
                                                up = tabFocusRequester
                                                down = if (hasHeroRail) heroRailFocusRequester else firstShelfFocusRequester
                                            },
                                        onOpenFilters = { showFilterOverlay = true },
                                        onSortChange = { next ->
                                            filtersByNavId[activeNavId] = topicFilters.copy(sort = next)
                                        },
                                        onFocused = { focusLayer = HomeV2FocusLayer.Filters }
                                    )
                                }
                            }

                            if (hasHeroRail) {
                                item(key = "hero_rail") {
                                    HomeV2HeroRail(
                                        items = heroRailItems,
                                        listState = heroRailListState,
                                        entryFocusRequester = heroRailFocusRequester,
                                        upFocusRequester = if (showFilters) filtersFocusRequester else tabFocusRequester,
                                        downFocusRequester = firstShelfFocusRequester,
                                        onFocused = { item ->
                                            focusLayer = HomeV2FocusLayer.HeroRail
                                            previewBackdropItem = item
                                        },
                                        onOpenDetail = onOpenDetail,
                                    )
                                }
                            }

                            // 推荐页的多层架子展示
                            if (!isCategoryPage) {
                                items(shelves, key = { it.title }) { shelf ->
                                    val isFirstShelf = shelves.firstOrNull()?.title == shelf.title
                                    HomeV2Shelf(
                                        shelf = shelf,
                                        entryFocusRequester = if (isFirstShelf) firstShelfFocusRequester else null,
                                        upFocusRequester = if (isFirstShelf) upFocusForContent else null,
                                        onFocused = { focusLayer = HomeV2FocusLayer.Shelf },
                                        onOpenDetail = onOpenDetail,
                                    )
                                }
                            }

                            // 分类页网格呈现，防止单行无限长
                            if (isCategoryPage && contentItems.isNotEmpty()) {
                                val chunked = contentItems.distinctBy { it.id }.chunked(7)
                                itemsIndexed(chunked, key = { index, _ -> "grid_row_$index" }) { rowIndex, rowItems ->
                                    val isFirstRow = rowIndex == 0
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = V2CardBleedPadding)
                                            .focusGroup()
                                            .then(if (isFirstRow) Modifier.focusProperties { up = upFocusForContent } else Modifier),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        rowItems.forEachIndexed { colIndex, item ->
                                            val isFirstItem = isFirstRow && colIndex == 0
                                            HomeV2PosterCard(
                                                item = item,
                                                modifier = Modifier
                                                    .then(if (isFirstItem) Modifier.focusRequester(firstShelfFocusRequester) else Modifier),
                                                onFocused = {
                                                    focusLayer = HomeV2FocusLayer.Shelf
                                                    previewBackdropItem = item
                                                },
                                                onClick = { onOpenDetail(item.id) },
                                            )
                                        }
                                    }
                                }

                                if (hasMoreContent) {
                                    item(key = "load_more") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 16.dp, bottom = 32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            HomeV2MoreCard(
                                                loading = loadingMoreContent,
                                                onFocused = { focusLayer = HomeV2FocusLayer.Shelf },
                                                onClick = { requestNextContentPage() }
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
private fun HomeV2Shell(
    backdropItem: TvPosterItem? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070B)),
    ) {
        Crossfade(
            targetState = backdropItem,
            animationSpec = tween(durationMillis = 420),
            label = "home_v2_backdrop_crossfade",
        ) { item ->
            AsyncImage(
                model = item?.backdropUrl?.ifBlank { item.posterUrl },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (item == null) 0f else 1f },
            )
        }
        content()
    }
}

@Composable
private fun HomeV2TopBar(
    navItems: List<TvNavItem>,
    activeNavId: Int,
    firstTabFocusRequester: FocusRequester,
    targetDownFocusRequester: FocusRequester,
    onTabFocus: () -> Unit,
    onSelectNav: (TvNavItem) -> Unit,
    onSearch: () -> Unit,
    onHistory: () -> Unit,
    onBackToLegacy: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .focusGroup()
                .focusProperties { down = targetDownFocusRequester },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navItems.forEach { item ->
                val isFirst = navItems.firstOrNull()?.id == item.id
                HomeV2TabChip(
                    text = item.name,
                    selected = item.id == activeNavId,
                    modifier = if (isFirst) Modifier.focusRequester(firstTabFocusRequester) else Modifier,
                    onFocused = onTabFocus,
                    onClick = { onSelectNav(item) },
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Row(
            modifier = Modifier
                .focusGroup()
                .focusProperties { down = targetDownFocusRequester },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeV2IconButton(
                icon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                onFocused = onTabFocus,
                onClick = onSearch,
            )
            HomeV2IconButton(
                icon = { Icon(Icons.Default.History, contentDescription = "播放历史") },
                onFocused = onTabFocus,
                onClick = onHistory,
            )
            if (onBackToLegacy != null) {
                HomeV2ActionChip(
                    text = "旧版",
                    icon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                    onFocused = onTabFocus,
                    onClick = onBackToLegacy,
                )
            }
        }
    }
}

@Composable
private fun HomeV2Hero(
    item: TvPosterItem,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(268.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.displaySmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 36.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(5.dp))
            val subtitle = buildHeroSubtitle(item)
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White.copy(alpha = 0.78f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 17.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun HomeV2FilterSummaryRow(
    filters: HomeV2TopicFilters,
    hasAdvancedFilters: Boolean,
    modifier: Modifier = Modifier,
    onOpenFilters: () -> Unit,
    onSortChange: (String) -> Unit,
    onFocused: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("by_default", "by_time", "by_hits", "by_score").forEach { sort ->
            HomeV2TabChip(
                text = sortLabel(sort),
                selected = filters.sort.ifBlank { "by_default" } == sort,
                onFocused = onFocused,
                onClick = { onSortChange(sort) },
            )
        }
        if (hasAdvancedFilters) {
            HomeV2ActionChip(
                text = "高级筛选",
                icon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                onFocused = onFocused,
                onClick = onOpenFilters,
            )
        }
        val summary = listOf(filters.classValue, filters.area, filters.year).filter { it.isNotBlank() }.joinToString(" · ")
        if (summary.isNotBlank()) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.labelLarge.copy(color = Color.White.copy(alpha = 0.65f), fontSize = 16.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeV2HeroRail(
    items: List<TvPosterItem>,
    listState: LazyListState,
    entryFocusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
    onFocused: (TvPosterItem) -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(134.dp)
            .focusGroup(),
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = V2CardBleedPadding, vertical = V2CardBleedPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(items.take(V2_HERO_RAIL_VISIBLE_LIMIT), key = { it.id }) { item ->
            val isFirst = items.firstOrNull()?.id == item.id
            HomeV2WideCard(
                item = item,
                modifier = Modifier
                    .then(if (isFirst) Modifier.focusRequester(entryFocusRequester) else Modifier)
                    .focusProperties {
                        up = upFocusRequester
                        down = downFocusRequester
                    },
                onFocused = { onFocused(item) },
                onClick = { onOpenDetail(item.id) },
            )
        }
    }
}

@Composable
private fun HomeV2Shelf(
    shelf: HomeV2ShelfModel,
    entryFocusRequester: FocusRequester?,
    upFocusRequester: FocusRequester?,
    onFocused: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = shelf.title,
            style = MaterialTheme.typography.titleLarge.copy(
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
            ),
            modifier = Modifier.padding(bottom = 6.dp, start = V2CardBleedPadding),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(212.dp)
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = V2CardBleedPadding, vertical = V2CardBleedPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(shelf.items.take(V2_SHELF_VISIBLE_LIMIT), key = { it.id }) { item ->
                val isFirst = shelf.items.firstOrNull()?.id == item.id
                HomeV2PosterCard(
                    item = item,
                    modifier = Modifier
                        .then(if (isFirst && entryFocusRequester != null) Modifier.focusRequester(entryFocusRequester) else Modifier)
                        .then(if (isFirst && upFocusRequester != null) Modifier.focusProperties { up = upFocusRequester } else Modifier),
                    onFocused = onFocused,
                    onClick = { onOpenDetail(item.id) },
                )
            }
        }
    }
}

@Composable
private fun HomeV2PosterCard(
    item: TvPosterItem,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.035f else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "home_v2_poster_scale",
    )
    Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
        modifier = modifier
            .width(122.dp)
            .consumeRepeatedDpadEvents()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (focused) 12.dp else 0.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.42f),
                spotColor = TvV2FocusGlow,
            )
            .tvFocusBorder(focused, shape, width = 1.5.dp, unfocusedWidth = 1.dp, unfocusedColor = TvDivider.copy(alpha = 0.14f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.66f)
                .clip(shape),
        ) {
            AsyncImage(
                model = item.posterUrl.ifBlank { item.backdropUrl },
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.68f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.82f),
                        ),
                    ),
            )
            val badge = item.label.ifBlank { item.remark }
            if (badge.isNotBlank()) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.66f), RoundedCornerShape(7.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = Color.White.copy(alpha = if (focused) 1f else 0.9f),
                    fontWeight = if (focused) FontWeight.ExtraBold else FontWeight.Bold,
                    fontSize = 14.sp,
                ),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeV2WideCard(
    item: TvPosterItem,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.035f else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "home_v2_wide_scale",
    )
    Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
        modifier = modifier
            .width(196.dp)
            .aspectRatio(1.77f)
            .consumeRepeatedDpadEvents()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (focused) 12.dp else 0.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.42f),
                spotColor = TvV2FocusGlow,
            )
            .tvFocusBorder(focused, shape, width = 1.5.dp, unfocusedWidth = 1.dp, unfocusedColor = TvDivider.copy(alpha = 0.14f)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.backdropUrl.ifBlank { item.posterUrl },
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            startY = 80f,
                        ),
                    ),
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                ),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeV2MoreCard(
    loading: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    HomeV2ActionCard(
        text = if (loading) "加载中..." else "查看更多",
        modifier = Modifier
            .width(122.dp)
            .aspectRatio(0.66f),
        onFocused = onFocused,
        onClick = onClick,
    )
}

@Composable
private fun HomeV2ActionCard(
    text: String,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.035f else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "home_v2_action_card_scale",
    )
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (focused) TvV2MaterialSurfaceFocused else Color.White.copy(alpha = 0.12f),
        contentColor = if (focused) Color(0xFF10141A) else Color.White.copy(alpha = 0.82f),
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
            .shadow(if (focused) 12.dp else 0.dp, shape, ambientColor = Color.Black.copy(alpha = 0.42f), spotColor = TvV2FocusGlow),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HomeV2TabChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = CircleShape
    Surface(
        onClick = onClick,
        shape = shape,
        color = when {
            focused -> TvV2MaterialSurfaceFocused
            selected -> Color.White.copy(alpha = 0.25f)
            else -> Color.Transparent
        },
        contentColor = when {
            focused -> Color(0xFF10141A)
            selected -> Color.White
            else -> Color.White.copy(alpha = 0.65f)
        },
        modifier = modifier
            .heightIn(min = 42.dp)
            .consumeRepeatedDpadEvents()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .shadow(if (focused) 8.dp else 0.dp, shape, spotColor = TvV2FocusGlow),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold, // 修正：无论焦点如何，始终保持相同的字重，避免改变物理宽度导致滚动条抖动
                fontSize = 15.sp,
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeV2ActionChip(
    text: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = CircleShape
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.035f else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "home_v2_action_scale",
    )
    Surface(
        onClick = onClick,
        shape = shape,
        color = when {
            focused -> TvV2MaterialSurfaceFocused
            primary -> Color.White.copy(alpha = 0.95f)
            else -> TvV2MaterialSurface
        },
        contentColor = when {
            focused || primary -> Color(0xFF10141A)
            else -> Color.White.copy(alpha = 0.9f)
        },
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
            .shadow(
                elevation = if (focused) 9.dp else 0.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.45f),
                spotColor = TvV2FocusGlow,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) { icon() }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HomeV2IconButton(
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.035f else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "home_v2_icon_btn_scale",
    )
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (focused) TvV2MaterialSurfaceFocused else TvV2MaterialSurface,
        contentColor = if (focused) Color(0xFF10141A) else Color.White.copy(alpha = 0.9f),
        modifier = modifier
            .size(38.dp)
            .consumeRepeatedDpadEvents()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(if (focused) 8.dp else 0.dp, CircleShape, spotColor = TvV2FocusGlow),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) { icon() }
        }
    }
}

@Composable
private fun HomeV2FilterOverlay(
    filterGroup: TvFilterGroup,
    filters: HomeV2TopicFilters,
    onDismiss: () -> Unit,
    onChange: (HomeV2TopicFilters) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xF20B1018),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .border(1.5.dp, TvDivider.copy(alpha = 0.4f), RoundedCornerShape(28.dp)),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(
                    text = "高级筛选",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                    ),
                )
                HomeV2FilterRow(
                    label = "排序",
                    options = filterGroup.sortValues.ifEmpty { listOf("by_default", "by_time", "by_hits", "by_score") }
                        .map { HomeV2FilterOption(sortLabel(it), it) },
                    selected = filters.sort.ifBlank { "by_default" },
                    onSelect = { onChange(filters.copy(sort = it)) },
                )
                if (filterGroup.classValues.drop(1).isNotEmpty()) {
                    HomeV2FilterRow(
                        label = "类型",
                        options = listOf(HomeV2FilterOption("全部", "")) + filterGroup.classValues.drop(1).map { HomeV2FilterOption(it, it) },
                        selected = filters.classValue,
                        onSelect = { onChange(filters.copy(classValue = it)) },
                    )
                }
                if (filterGroup.areaValues.drop(1).isNotEmpty()) {
                    HomeV2FilterRow(
                        label = "地区",
                        options = listOf(HomeV2FilterOption("全部", "")) + filterGroup.areaValues.drop(1).map { HomeV2FilterOption(it, it) },
                        selected = filters.area,
                        onSelect = { onChange(filters.copy(area = it)) },
                    )
                }
                if (filterGroup.yearValues.drop(1).isNotEmpty()) {
                    HomeV2FilterRow(
                        label = "年份",
                        options = listOf(HomeV2FilterOption("全部", "")) + filterGroup.yearValues.drop(1).map { HomeV2FilterOption(it, it) },
                        selected = filters.year,
                        onSelect = { onChange(filters.copy(year = it)) },
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    HomeV2ActionChip(
                        text = "完成筛选",
                        icon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                        primary = true,
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeV2FilterRow(
    label: String,
    options: List<HomeV2FilterOption>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Bold,
            ),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier.focusGroup(),
        ) {
            items(options, key = { "$label-${it.value}" }) { option ->
                HomeV2TabChip(
                    text = option.label,
                    selected = selected == option.value,
                    onFocused = {},
                    onClick = { onSelect(option.value) },
                )
            }
        }
    }
}

private data class HomeV2TopicFilters(
    val sort: String = "by_default",
    val classValue: String = "",
    val area: String = "",
    val year: String = "",
)

private data class HomeV2ScreenQueryKey(
    val navId: Int,
    val sort: String,
    val classValue: String,
    val area: String,
    val year: String,
)

private data class HomeV2CachedScreenContent(
    val items: List<TvPosterItem>,
    val nextPage: Int,
    val hasMore: Boolean,
)

private data class HomeV2ShelfModel(
    val title: String,
    val items: List<TvPosterItem>,
)

private data class HomeV2FilterOption(
    val label: String,
    val value: String,
)

private fun buildHomeV2NavItems(items: List<TvNavItem>): List<TvNavItem> {
    if (items.isEmpty()) {
        return listOf(
            TvNavItem(id = 0, name = "推荐"),
            TvNavItem(id = 1, name = "电影"),
            TvNavItem(id = 2, name = "剧集"),
            TvNavItem(id = 3, name = "综艺"),
            TvNavItem(id = 4, name = "动漫"),
            TvNavItem(id = 36, name = "短剧"),
            TvNavItem(id = 26, name = "福利"),
        )
    }
    val result = items.filter { it.id > 0 && it.name != "推荐" }.toMutableList()
    result.add(0, TvNavItem(id = 0, name = "推荐"))
    return result.distinctBy { it.id }
}

private fun buildHomeV2HeroItem(
    feed: TvHomeFeed,
    activeNavId: Int,
    contentItems: List<TvPosterItem>,
): TvPosterItem? {
    return if (activeNavId == 0) {
        feed.banners.firstOrNull()
            ?: feed.sections.firstOrNull()?.items?.firstOrNull()
            ?: contentItems.firstOrNull()
    } else {
        contentItems.firstOrNull()
            ?: feed.banners.firstOrNull()
            ?: feed.sections.firstOrNull()?.items?.firstOrNull()
    }
}

private fun buildHomeV2HeroRailItems(
    feed: TvHomeFeed,
    activeNavId: Int,
): List<TvPosterItem> {
    // 修复：分类页禁止拉取 HeroRail，否则它会和底部的 Grid 内容发生 100% 重复！
    if (activeNavId != 0) return emptyList() 
    
    val source = feed.banners.plus(feed.sections.flatMap { it.items })
    return source.distinctBy { it.id }.take(V2_HERO_RAIL_VISIBLE_LIMIT)
}

private fun buildHomeV2Shelves(
    feed: TvHomeFeed,
    contentItems: List<TvPosterItem>,
): List<HomeV2ShelfModel> {
    val serverShelves = feed.sections
        .mapNotNull { section ->
            val items = section.items.distinctBy { it.id }
            if (items.isEmpty()) null else HomeV2ShelfModel(section.title.ifBlank { "推荐" }, items)
        }

    if (serverShelves.isNotEmpty()) return serverShelves

    val fallbackItems = feed.banners.plus(contentItems).distinctBy { it.id }
    return if (fallbackItems.isEmpty()) emptyList() else listOf(HomeV2ShelfModel("推荐", fallbackItems))
}

private fun buildHeroSubtitle(item: TvPosterItem): String {
    return listOf(item.year, item.category, item.score.takeIf { it.isNotBlank() }?.let { "评分 $it" })
        .filterNotNull()
        .filter { it.isNotBlank() }
        .ifEmpty { listOf(item.remark.ifBlank { item.overview.takeIf { it.isNotBlank() } }) }
        .filterNotNull()
        .joinToString(" · ")
}

private fun sortLabel(value: String): String {
    return when (value.ifBlank { "by_default" }) {
        "by_default" -> "综合"
        "by_time" -> "最新"
        "by_hits" -> "最热"
        "by_score" -> "评分"
        else -> value
    }
}

private fun TvFilterGroup.hasAdvancedFilters(): Boolean {
    return classValues.drop(1).isNotEmpty() || areaValues.drop(1).isNotEmpty() || yearValues.drop(1).isNotEmpty()
}
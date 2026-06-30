package com.globalvision.tvlite.core.network

import android.content.Context
import android.util.Log
import com.globalvision.tvlite.core.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class TvRepository(
    context: Context? = null,
    private val api: TvApiClient = TvApiClient(context),
) {
    @Volatile
    private var homeFeedCache: TvHomeFeed? = null
    @Volatile
    private var searchRankingCache: List<TvSearchKeywordGroup>? = null
    @Volatile
    private var searchLatelyWordsCache: List<TvSearchKeywordItem>? = null
    @Volatile
    private var movieTopicsCache: List<TvMovieTopic>? = null
    @Volatile
    private var movieRankingsCache: List<TvMovieRanking>? = null

    private val detailCache = ConcurrentHashMap<String, TvMovieDetail>()
    private val posterFallbackCache = ConcurrentHashMap<String, String>()
    private val missingPosterFallbackIds = ConcurrentHashMap.newKeySet<String>()
    private val episodesCache = ConcurrentHashMap<EpisodeCacheKey, List<TvEpisode>>()
    private val movieRecommendationsCache = ConcurrentHashMap<String, List<TvPosterItem>>()
    private val movieTopicDetailCache = ConcurrentHashMap<String, TvMovieTopic>()
    private val movieRankingItemsCache = ConcurrentHashMap<String, List<TvPosterItem>>()
    private val weeklyMoviesCache = ConcurrentHashMap<Int, List<TvPosterItem>>()
    private val screenMoviesPageCache = ConcurrentHashMap<ScreenMoviesQuery, ConcurrentHashMap<Int, List<TvPosterItem>>>()
    private val screenMoviesStateCache = ConcurrentHashMap<ScreenMoviesQuery, ScreenMoviesState>()

    suspend fun getHomeFeed(): TvHomeFeed = withContext(Dispatchers.IO) {
        homeFeedCache?.let {
            Log.d(TAG, "home feed cache hit")
            return@withContext it
        }
        val (config, recommend) = supervisorScope {
            val configDeferred = async {
                try {
                    fetchAppConfig()
                } catch (throwable: Throwable) {
                    Log.e(TAG, "app config request failed", throwable)
                    emptyConfig()
                }
            }
            val recommendDeferred = async {
                try {
                    api.get("/movie/index_recommend")
                } catch (throwable: Throwable) {
                    Log.e(TAG, "home recommend request failed", throwable)
                    null
                }
            }
            configDeferred.await() to recommendDeferred.await()
        }
        if (recommend == null) {
            Log.w(TAG, "home recommend request failed, using sample feed")
            return@withContext sampleHomeFeed(config).also {
                homeFeedCache = it
            }
        }
        val feed = mapHomeFeed(config, recommend)
        Log.d(TAG, "home feed loaded: nav=${feed.config.topNav.size}, banners=${feed.banners.size}, sections=${feed.sections.size}")
        homeFeedCache = feed
        feed
    }

    fun peekHomeFeed(): TvHomeFeed? = homeFeedCache

    suspend fun search(keyword: String, page: Int = 1): TvSearchResult = withContext(Dispatchers.IO) {
        val response = api.get(
            "/movie/search",
            mapOf(
                "keyword" to keyword,
                "page" to page,
                "pageSize" to SEARCH_PAGE_SIZE,
                "res_type" to "by_movie_name",
            ),
        )
        val data = response.optJSONObject("data") ?: JSONObject()
        val resolvedPage = data.optInt("page", page).coerceAtLeast(1)
        val resolvedPageSize = data.optInt("pageSize", SEARCH_PAGE_SIZE).coerceAtLeast(SEARCH_PAGE_SIZE)
        val result = TvSearchResult(
            items = data.optJSONArray("list").toPosterItems(),
            page = resolvedPage,
            pageSize = resolvedPageSize,
            total = data.optInt("total", 0),
        )
        Log.d(
            TAG,
            "search loaded: keyword=$keyword page=${result.page} pageSize=${result.pageSize} total=${result.total} items=${result.items.size}",
        )
        result
    }

    suspend fun getSearchAutocomplete(keyword: String): List<TvSearchKeywordItem> = withContext(Dispatchers.IO) {
        val normalized = keyword.trim()
        if (normalized.isBlank()) return@withContext emptyList()
        val response = runCatching { api.get("/movie/search_complete", mapOf("keyword" to normalized)) }
            .getOrNull() ?: return@withContext emptyList()
        val hints = response.extractSearchWords()
        Log.d(TAG, "search autocomplete loaded: keyword=$normalized size=${hints.size}")
        hints
    }

    suspend fun getSearchRanking(): List<TvSearchKeywordGroup> = withContext(Dispatchers.IO) {
        searchRankingCache?.let {
            Log.d(TAG, "search ranking cache hit: size=${it.size}")
            return@withContext it
        }
        val response = runCatching { api.get("/movie/search_ranking") }.getOrNull()
            ?: return@withContext defaultSearchGroups().also { searchRankingCache = it }
        val hints = response.extractSearchKeywordGroups(fromRanking = true)
        val result = if (hints.isNotEmpty()) hints else defaultSearchGroups()
        searchRankingCache = result
        Log.d(TAG, "search ranking loaded: size=${result.size}")
        result
    }

    suspend fun getSearchLatelyWords(): List<TvSearchKeywordItem> = withContext(Dispatchers.IO) {
        searchLatelyWordsCache?.let {
            Log.d(TAG, "search lately cache hit: size=${it.size}")
            return@withContext it
        }
        val response = runCatching { api.get("/movie/search_lately_words") }.getOrNull()
            ?: return@withContext emptyList()
        val hints = response.extractSearchWords()
        searchLatelyWordsCache = hints
        Log.d(TAG, "search lately loaded: size=${hints.size}")
        hints
    }

    suspend fun getSearchHints(): List<TvSearchKeywordItem> = withContext(Dispatchers.IO) {
        val ranking = getSearchRanking()
        if (ranking.isNotEmpty()) return@withContext ranking.firstOrNull()?.items.orEmpty()
        val lately = getSearchLatelyWords()
        if (lately.isNotEmpty()) return@withContext lately
        defaultSearchGroups().firstOrNull()?.items.orEmpty()
    }

    suspend fun getScreenMovies(
        typeId: Int,
        sort: String = "by_default",
        classValue: String? = null,
        area: String? = null,
        year: String? = null,
        page: Int = 1,
        pageSize: Int = 30,
    ): ScreenMoviesResult = withContext(Dispatchers.IO) {
        val query = ScreenMoviesQuery(
            typeId = typeId,
            sort = sort,
            classValue = classValue.orEmpty(),
            area = area.orEmpty(),
            year = year.orEmpty(),
        )
        screenMoviesPageCache[query]?.get(page)?.let {
            Log.d(TAG, "screen list cache hit: typeId=$typeId sort=$sort class=${classValue.orEmpty()} area=${area.orEmpty()} year=${year.orEmpty()} page=$page items=${it.size}")
            val cachedState = screenMoviesStateCache[query]
            return@withContext ScreenMoviesResult(
                items = it,
                nextPage = maxOf(page + 1, cachedState?.nextPage ?: (page + 1)),
                hasMore = cachedState?.hasMore ?: it.isNotEmpty(),
                total = cachedState?.total ?: 0,
            )
        }

        val response = api.get(
            "/movie/screen/list",
            buildMap {
                put("type_id", typeId)
                put("sort", sort)
                put("page", page)
                put("pageSize", pageSize)
                if (!classValue.isNullOrBlank()) put("class", classValue)
                if (!area.isNullOrBlank()) put("area", area)
                if (!year.isNullOrBlank()) put("year", year)
            },
        )
        val data = response.optJSONObject("data") ?: response
        val list = data.optJSONArray("list").toPosterItems()
        val total = data.optInt("total", 0)
        val pageCache = screenMoviesPageCache.getOrPut(query) { ConcurrentHashMap() }
        pageCache[page] = list
        val nextPage = page + 1
        val hasMore = list.isNotEmpty()
        val currentState = screenMoviesStateCache[query]
        screenMoviesStateCache[query] = if (page == 1 || currentState == null) {
            ScreenMoviesState(
                items = list,
                nextPage = nextPage,
                hasMore = hasMore,
                total = total,
            )
        } else {
            ScreenMoviesState(
                items = currentState.items + list,
                nextPage = nextPage,
                hasMore = hasMore,
                total = if (total > 0) total else currentState.total,
            )
        }
        Log.d(
            TAG,
            "screen list loaded: typeId=$typeId sort=$sort class=${classValue.orEmpty()} area=${area.orEmpty()} year=${year.orEmpty()} page=$page total=$total items=${list.size} hasMore=$hasMore",
        )
        ScreenMoviesResult(
            items = list,
            nextPage = nextPage,
            hasMore = hasMore,
            total = total,
        )
    }

    fun peekScreenMoviesState(
        typeId: Int,
        sort: String = "by_default",
        classValue: String? = null,
        area: String? = null,
        year: String? = null,
    ): ScreenMoviesState? {
        val query = ScreenMoviesQuery(
            typeId = typeId,
            sort = sort,
            classValue = classValue.orEmpty(),
            area = area.orEmpty(),
            year = year.orEmpty(),
        )
        return screenMoviesStateCache[query]
    }

    fun peekScreenMoviesPage(
        typeId: Int,
        sort: String = "by_default",
        classValue: String? = null,
        area: String? = null,
        year: String? = null,
        page: Int = 1,
    ): List<TvPosterItem>? {
        val query = ScreenMoviesQuery(
            typeId = typeId,
            sort = sort,
            classValue = classValue.orEmpty(),
            area = area.orEmpty(),
            year = year.orEmpty(),
        )
        return screenMoviesPageCache[query]?.get(page)
    }

    suspend fun getDetail(id: String): TvMovieDetail = withContext(Dispatchers.IO) {
        detailCache[id]?.let {
            Log.d(TAG, "detail cache hit: id=$id")
            return@withContext it
        }
        val response = api.get("/movie/detail", mapOf("id" to id))
        val data = response.optJSONObject("data") ?: JSONObject()
        val detail = mapDetail(data)
        Log.d(
            TAG,
            buildString {
                append("detail loaded: id=").append(id)
                append(" title=").append(detail.title)
                append(" sources=").append(detail.sources.size)
                detail.sources.forEachIndexed { index, source ->
                    val firstEpisode = source.episodes.firstOrNull()
                    append(" | #").append(index)
                    append(" ").append(source.code)
                    append(":").append(source.name)
                    append(" episodes=").append(source.episodes.size)
                    append(" firstReady=").append(firstEpisode?.readyToPlay)
                    append(" firstPlayUrl=").append(firstEpisode?.playUrl.orEmpty().take(48))
                }
            },
        )
        detailCache[id] = detail
        detail
    }

    suspend fun getPosterFallbackUrl(movieId: String): String = withContext(Dispatchers.IO) {
        if (movieId.isBlank()) return@withContext ""
        posterFallbackCache[movieId]?.let { return@withContext it }
        if (missingPosterFallbackIds.contains(movieId)) return@withContext ""

        val fallbackUrl = runCatching { getDetail(movieId).posterUrl.trim() }
            .getOrDefault("")
        if (fallbackUrl.isBlank()) {
            missingPosterFallbackIds += movieId
            return@withContext ""
        }
        posterFallbackCache[movieId] = fallbackUrl
        fallbackUrl
    }

    suspend fun getMovieRecommendations(movieId: String): List<TvPosterItem> = withContext(Dispatchers.IO) {
        if (movieId.isBlank()) return@withContext emptyList()
        movieRecommendationsCache[movieId]?.let {
            Log.d(TAG, "movie recommendations cache hit: movieId=$movieId size=${it.size}")
            return@withContext it
        }
        val response = api.get("/movie/$movieId/recommend")
        val items = response.extractPosterItems()
        movieRecommendationsCache[movieId] = items
        Log.d(TAG, "movie recommendations loaded: movieId=$movieId size=${items.size}")
        items
    }

    suspend fun getMovieTopics(): List<TvMovieTopic> = withContext(Dispatchers.IO) {
        movieTopicsCache?.let {
            Log.d(TAG, "movie topics cache hit: size=${it.size}")
            return@withContext it
        }
        val response = api.get("/movie/topic")
        val topics = response.extractMovieTopics()
        movieTopicsCache = topics
        Log.d(TAG, "movie topics loaded: size=${topics.size}")
        topics
    }

    suspend fun getMovieTopicDetail(topicId: String): TvMovieTopic = withContext(Dispatchers.IO) {
        val normalizedId = topicId.trim()
        if (normalizedId.isBlank()) {
            return@withContext TvMovieTopic(id = "", title = "专题")
        }
        movieTopicDetailCache[normalizedId]?.let {
            Log.d(TAG, "movie topic detail cache hit: id=$normalizedId items=${it.items.size}")
            return@withContext it
        }
        val response = api.get("/movie/topic/$normalizedId")
        val detail = response.extractMovieTopicDetail()
        movieTopicDetailCache[normalizedId] = detail
        Log.d(TAG, "movie topic detail loaded: id=$normalizedId items=${detail.items.size}")
        detail
    }

    suspend fun getMovieRankings(): List<TvMovieRanking> = withContext(Dispatchers.IO) {
        movieRankingsCache?.let {
            Log.d(TAG, "movie rankings cache hit: size=${it.size}")
            return@withContext it
        }
        val response = api.get("/movie/ranking/list")
        val rankings = response.extractMovieRankings()
        movieRankingsCache = rankings
        Log.d(TAG, "movie rankings loaded: size=${rankings.size}")
        rankings
    }

    suspend fun getMovieRankingItems(rankingId: String): List<TvPosterItem> = withContext(Dispatchers.IO) {
        val normalizedId = rankingId.trim()
        if (normalizedId.isBlank()) return@withContext emptyList()
        movieRankingItemsCache[normalizedId]?.let {
            Log.d(TAG, "movie ranking items cache hit: id=$normalizedId size=${it.size}")
            return@withContext it
        }
        val response = api.get("/movie/ranking/data", mapOf("id" to normalizedId))
        val items = response.extractPosterItems()
        movieRankingItemsCache[normalizedId] = items
        Log.d(TAG, "movie ranking items loaded: id=$normalizedId size=${items.size}")
        items
    }

    suspend fun getWeeklyMovies(weekDay: Int): List<TvPosterItem> = withContext(Dispatchers.IO) {
        val normalizedDay = weekDay.coerceIn(1, 7)
        weeklyMoviesCache[normalizedDay]?.let {
            Log.d(TAG, "weekly movies cache hit: weekDay=$normalizedDay size=${it.size}")
            return@withContext it
        }
        val response = api.get("/movie/weekly", mapOf("week_day" to normalizedDay))
        val items = response.extractPosterItems()
        weeklyMoviesCache[normalizedDay] = items
        Log.d(TAG, "weekly movies loaded: weekDay=$normalizedDay size=${items.size}")
        items
    }

    suspend fun getEpisodes(movieId: String, fromCode: String): List<TvEpisode> = withContext(Dispatchers.IO) {
        val cacheKey = EpisodeCacheKey(movieId = movieId, fromCode = fromCode)
        episodesCache[cacheKey]?.let {
            Log.d(TAG, "episodes cache hit: movieId=$movieId fromCode=$fromCode size=${it.size}")
            return@withContext it
        }
        val response = api.get("/movie_addr/list", mapOf("movie_id" to movieId, "from_code" to fromCode))
        response.optJSONArray("data").toEpisodes(fromCode).also { episodes ->
            episodesCache[cacheKey] = episodes
        }
    }

    suspend fun resolveEpisodeUrl(episode: TvEpisode): String = withContext(Dispatchers.IO) {
        resolveEpisodeUrlInternal(episode)
    }

    suspend fun resolveEpisodeUrlWithParseFallback(episode: TvEpisode): String = withContext(Dispatchers.IO) {
        resolveEpisodeUrlInternal(episode, forceParse = true)
    }

    suspend fun isPlayableMediaUrl(url: String): Boolean = withContext(Dispatchers.IO) {
        val normalized = url.trim()
        if (normalized.isBlank()) return@withContext false

        runCatching {
            val connection = (URL(normalized).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                instanceFollowRedirects = false
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; TV)")
            }
            connection.connect()
            val code = connection.responseCode
            connection.disconnect()
            code in 200..299
        }.getOrDefault(false)
    }

    private suspend fun fetchAppConfig(): TvAppConfig {
        val response = api.get("/app/config")
        val data = response.optJSONObject("data") ?: response
        return mapConfig(data)
    }

    private fun emptyConfig(): TvAppConfig = TvAppConfig(
        topNav = fallbackTopNav(),
        filterGroups = fallbackFilterGroups(),
    )

    data class ScreenMoviesState(
        val items: List<TvPosterItem>,
        val nextPage: Int,
        val hasMore: Boolean,
        val total: Int,
    )

    data class ScreenMoviesResult(
        val items: List<TvPosterItem>,
        val nextPage: Int,
        val hasMore: Boolean,
        val total: Int,
    )

    private data class ScreenMoviesQuery(
        val typeId: Int,
        val sort: String,
        val classValue: String,
        val area: String,
        val year: String,
    )

    private data class EpisodeCacheKey(
        val movieId: String,
        val fromCode: String,
    )

    private fun mapConfig(payload: JSONObject): TvAppConfig {
        val nav = payload.optJSONArray("index_top_nav").toNavItems()
        val screenConfig = payload.optJSONObject("movie_screen")
        val defaultSortValues = screenConfig?.optJSONArray("sort").toStringList()
        val filterGroups = screenConfig
            ?.optJSONArray("filter")
            .toFilterGroups(defaultSortValues)
        return TvAppConfig(
            topNav = nav.ifEmpty { fallbackTopNav() },
            filterGroups = filterGroups.ifEmpty { fallbackFilterGroups(defaultSortValues) },
        )
    }

    private fun mapHomeFeed(config: TvAppConfig, recommendPayload: JSONObject): TvHomeFeed {
        val data = recommendPayload.optJSONArray("data") ?: JSONArray()
        val banners = mutableListOf<TvPosterItem>()
        val sections = mutableListOf<TvHomeSection>()

        for (i in 0 until data.length()) {
            val group = data.optJSONObject(i) ?: continue
            if (isAdvertisementGroup(group)) continue
            val layout = group.optString("layout").ifBlank { group.optString("type") }
            val items = group.optJSONArray("list").toRecommendPosterItems()
            if (layout.contains("carousel", ignoreCase = true)) {
                banners += items
            } else if (items.isNotEmpty()) {
                sections += TvHomeSection(
                    title = group.optString("title").ifBlank { group.optString("name").ifBlank { "推荐" } },
                    items = items,
                )
            }
        }

        return TvHomeFeed(config = config, banners = banners, sections = sections)
    }

    private fun mapDetail(data: JSONObject): TvMovieDetail {
        val sources = data.optJSONArray("play_from").toPlaySources()
        return TvMovieDetail(
            id = data.optString("id"),
            title = data.optString("name"),
            posterUrl = normalizeImage(data.optString("cover")),
            year = data.optString("year"),
            area = data.optString("area"),
            director = data.optString("director"),
            writer = data.optString("writer"),
            actor = data.optString("actor"),
            content = data.optString("content"),
            remarks = data.optString("remarks"),
            sources = sources,
        )
    }

    private fun sampleHomeFeed(config: TvAppConfig): TvHomeFeed {
        val sample = List(10) { index ->
            TvPosterItem(
                id = "sample-$index",
                title = "示例影片 ${index + 1}",
                posterUrl = "",
                year = "2025",
                score = "8.${index}",
                category = "电影",
                remark = "Demo",
                overview = "网络异常时的占位内容。",
            )
        }
        return TvHomeFeed(
            config = config,
            banners = sample.take(3),
            sections = listOf(TvHomeSection("推荐", sample)),
        )
    }

    private fun normalizeImage(value: String): String {
        return when {
            value.isBlank() -> ""
            value.startsWith("//") -> "https:$value"
            else -> value
        }
    }

    private fun JSONArray?.toNavItems(): List<TvNavItem> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = optJSONObject(i) ?: continue
                add(TvNavItem(id = item.optInt("id"), name = item.optString("name")))
            }
        }
    }

    private fun JSONArray?.toFilterGroups(defaultSortValues: List<String> = emptyList()): List<TvFilterGroup> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = optJSONObject(i) ?: continue
                val sortValues = item.optJSONArray("sort").toStringList()
                add(
                    TvFilterGroup(
                        id = item.optInt("id"),
                        name = item.optString("name"),
                        classValues = item.optJSONArray("class").toStringList(),
                        areaValues = item.optJSONArray("area").toStringList(),
                        yearValues = item.optJSONArray("year").toStringList(),
                        sortValues = sortValues.ifEmpty { defaultSortValues.ifEmpty { fallbackSortValues() } },
                    ),
                )
            }
        }
    }

    private fun JSONArray?.toPosterItems(): List<TvPosterItem> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = optJSONObject(i) ?: continue
                if (isAdvertisementItem(item)) continue
                val title = item.optString("name")
                    .ifBlank { item.optString("title") }
                    .ifBlank { item.optString("vod_name") }
                val poster = item.optString("cover")
                    .ifBlank { item.optString("poster") }
                    .ifBlank { item.optString("image") }
                    .ifBlank { item.optString("vod_pic") }
                add(
                    TvPosterItem(
                        id = item.optString("id").ifBlank { item.optString("click").ifBlank { title } },
                        title = title,
                        posterUrl = normalizeImage(poster),
                        backdropUrl = normalizeImage(item.optString("backdrop")),
                        label = item.optString("label"),
                        year = item.optString("year"),
                        score = item.optString("score"),
                        category = item.optString("type_name"),
                        remark = item.optString("remarks").ifBlank { item.optString("label") },
                        overview = item.optString("highlight").ifBlank { item.optString("dynamic") },
                    ),
                )
            }
        }
    }

    private fun JSONObject.extractPosterItems(): List<TvPosterItem> {
        val data = opt("data")
        return when (data) {
            is JSONArray -> data.toPosterItems()
            is JSONObject -> buildList {
                addAll(data.optJSONArray("list").toPosterItems())
                addAll(data.optJSONArray("items").toPosterItems())
                addAll(data.optJSONArray("recommend").toPosterItems())
                addAll(data.optJSONArray("data").toPosterItems())
            }
            else -> buildList {
                addAll(optJSONArray("list").toPosterItems())
                addAll(optJSONArray("items").toPosterItems())
                addAll(optJSONArray("recommend").toPosterItems())
            }
        }.distinctBy { it.id }
    }

    private fun JSONObject.extractMovieTopics(): List<TvMovieTopic> {
        return extractCollectionArray("topic", "topics", "list", "items", "data")
            .toMovieTopics()
    }

    private fun JSONObject.extractMovieTopicDetail(): TvMovieTopic {
        val data = optJSONObject("data") ?: this
        return data.toMovieTopic() ?: TvMovieTopic(id = data.optString("id"), title = "专题")
    }

    private fun JSONObject.extractMovieRankings(): List<TvMovieRanking> {
        return extractCollectionArray("ranking", "rankings", "list", "items", "data")
            .toMovieRankings()
    }

    private fun JSONObject.extractCollectionArray(vararg keys: String): JSONArray? {
        val data = opt("data")
        if (data is JSONArray) return data
        if (data is JSONObject) {
            keys.forEach { key ->
                data.optJSONArray(key)?.let { return it }
            }
        }
        keys.forEach { key ->
            optJSONArray(key)?.let { return it }
        }
        return null
    }

    private fun JSONArray?.toMovieTopics(): List<TvMovieTopic> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = optJSONObject(i) ?: continue
                item.toMovieTopic()?.let { add(it) }
            }
        }.distinctBy { it.id }
    }

    private fun JSONObject.toMovieTopic(): TvMovieTopic? {
        val title = optString("name")
            .ifBlank { optString("title") }
            .ifBlank { optString("label") }
        if (title.isBlank()) return null
        val id = optString("id")
            .ifBlank { optString("topic_id") }
            .ifBlank { optString("type_id") }
            .ifBlank { title }
        val cover = optString("cover")
            .ifBlank { optString("poster") }
            .ifBlank { optString("image") }
            .ifBlank { optString("pic") }
        return TvMovieTopic(
            id = id,
            title = title,
            coverUrl = normalizeImage(cover),
            description = optString("description")
                .ifBlank { optString("desc") }
                .ifBlank { optString("content") },
            movieCount = optInt("movie_count", 0),
            viewCount = optString("view").ifBlank { optString("views") },
            items = extractNestedPosterItems(),
        )
    }

    private fun JSONArray?.toMovieRankings(): List<TvMovieRanking> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = optJSONObject(i) ?: continue
                val childRankings = item.extractNestedRankings()
                if (childRankings.isNotEmpty()) {
                    addAll(childRankings)
                } else {
                    item.toMovieRanking()?.let { add(it) }
                }
            }
        }.distinctBy { it.id }
    }

    private fun JSONObject.toMovieRanking(): TvMovieRanking? {
        val title = optString("name")
            .ifBlank { optString("title") }
            .ifBlank { optString("label") }
        if (title.isBlank()) return null
        val id = optString("id")
            .ifBlank { optString("ranking_id") }
            .ifBlank { optString("rank_id") }
            .ifBlank { optString("type_id") }
            .ifBlank { title }
        return TvMovieRanking(
            id = id,
            title = title,
            items = extractNestedPosterItems(),
        )
    }

    private fun JSONObject.extractNestedRankings(): List<TvMovieRanking> {
        val nestedArrays = listOf(
            optJSONArray("list"),
            optJSONArray("items"),
            optJSONArray("ranking"),
            optJSONArray("rankings"),
            optJSONArray("children"),
        )
        return nestedArrays
            .asSequence()
            .filterNotNull()
            .flatMap { array ->
                sequence {
                    for (i in 0 until array.length()) {
                        val child = array.optJSONObject(i) ?: continue
                        val ranking = child.toMovieRanking() ?: continue
                        if (ranking.id.isNotBlank()) yield(ranking)
                    }
                }
            }
            .toList()
            .distinctBy { it.id }
    }

    private fun JSONObject.extractNestedPosterItems(): List<TvPosterItem> {
        return buildList {
            addAll(optJSONArray("list").toPosterItems())
            addAll(optJSONArray("items").toPosterItems())
            addAll(optJSONArray("movies").toPosterItems())
            addAll(optJSONArray("movie_list").toPosterItems())
            addAll(optJSONArray("movie_data").toPosterItems())
            addAll(optJSONArray("data").toPosterItems())
        }.distinctBy { it.id }
    }

    private fun JSONArray?.toRecommendPosterItems(): List<TvPosterItem> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = optJSONObject(i) ?: continue
                if (isAdvertisementItem(item) || isPromotionalRecommendItem(item)) continue
                val title = item.optString("name")
                    .ifBlank { item.optString("title") }
                    .ifBlank { item.optString("vod_name") }
                val poster = item.optString("cover")
                    .ifBlank { item.optString("poster") }
                    .ifBlank { item.optString("image") }
                    .ifBlank { item.optString("vod_pic") }
                add(
                    TvPosterItem(
                        id = item.optString("id").ifBlank { item.optString("click").ifBlank { title } },
                        title = title,
                        posterUrl = normalizeImage(poster),
                        backdropUrl = normalizeImage(item.optString("backdrop")),
                        label = item.optString("label"),
                        year = item.optString("year"),
                        score = item.optString("score"),
                        category = item.optString("type_name"),
                        remark = item.optString("remarks").ifBlank { item.optString("label") },
                        overview = item.optString("highlight").ifBlank { item.optString("dynamic") },
                    ),
                )
            }
        }
    }

    private fun JSONArray?.toEpisodes(fromCode: String): List<TvEpisode> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = optJSONObject(i) ?: continue
                if (i == 0) {
                    Log.d(TAG, "first episode raw: fromCode=$fromCode item=$item")
                }
                add(
                    TvEpisode(
                        episodeId = item.optInt("episode_id"),
                        name = item.optString("episode_name").ifBlank { item.optString("name") },
                        playUrl = item.optString("play_url"),
                        fromCode = item.optString("from_code").ifBlank { fromCode },
                        parseUrl = item.optString("parseUrl").ifBlank { item.optString("parse_url") },
                        readyToPlay = item.optBoolean("ready_to_play", false),
                    ),
                )
            }
        }
    }

    private fun JSONArray?.toPlaySources(): List<TvPlaySource> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = optJSONObject(i) ?: continue
                val code = item.optString("code").ifBlank { item.optString("from_code") }
                val name = item.optString("name").ifBlank { item.optString("from_name") }
                add(
                    TvPlaySource(
                        code = code,
                        name = name,
                        episodes = item.optJSONArray("list").toEpisodes(code),
                    ),
                )
            }
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val value = opt(i)
                val text = when (value) {
                    is String -> value.trim()
                    is JSONObject -> value.optString("value")
                        .ifBlank { value.optString("id") }
                        .ifBlank { value.optString("name") }
                        .ifBlank { value.optString("title") }
                        .ifBlank { value.optString("label") }
                        .ifBlank { value.optString("word") }
                    else -> value?.toString().orEmpty().trim()
                }
                if (text.isNotBlank()) {
                    add(text)
                }
            }
        }.distinct()
    }

    private fun JSONArray?.toSearchKeywordItems(): List<TvSearchKeywordItem> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val value = opt(i)
                when (value) {
                    is String -> {
                        val word = value.trim()
                        if (word.isNotBlank()) {
                            add(TvSearchKeywordItem(word = word))
                        }
                    }
                    is JSONObject -> {
                        val word = value.optString("word")
                            .ifBlank { value.optString("name") }
                            .ifBlank { value.optString("title") }
                            .ifBlank { value.optString("keyword") }
                            .ifBlank { value.optString("label") }
                        if (word.isNotBlank()) {
                            add(
                                TvSearchKeywordItem(
                                    word = word.trim(),
                                    hot = value.optInt("hot").takeIf { it > 0 },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun isAdvertisementGroup(group: JSONObject): Boolean {
        val layout = group.optString("layout")
        val title = group.optString("title")
        return layout.contains("advert", ignoreCase = true) ||
            title.contains("广告")
    }

    private fun isAdvertisementItem(item: JSONObject): Boolean {
        val title = item.optString("name")
            .ifBlank { item.optString("title") }
            .ifBlank { item.optString("vod_name") }
        val label = item.optString("label")
        val remark = item.optString("remarks")
        val itemType = item.optString("type")
        return title.contains("广告") ||
            label.contains("广告") ||
            remark.contains("广告") ||
            itemType.contains("advert", ignoreCase = true)
    }

    private fun JSONObject.extractSearchKeywordGroups(fromRanking: Boolean = false): List<TvSearchKeywordGroup> {
        val resultGroups = mutableListOf<TvSearchKeywordGroup>()

        if (fromRanking) {
            val dataGroups = optJSONArray("data")
            if (dataGroups != null) {
                for (i in 0 until dataGroups.length()) {
                    val group = dataGroups.optJSONObject(i) ?: continue
                    val title = group.optString("title")
                        .ifBlank { group.optString("name") }
                        .ifBlank { group.optString("label") }
                        .ifBlank { "热搜" }
                    val items = group.optJSONArray("list").toSearchKeywordItems()
                    if (items.isNotEmpty()) {
                        resultGroups += TvSearchKeywordGroup(title = title, items = items)
                    }
                }
            }
            return resultGroups
        } else {
            val items = buildList {
                addAll(optJSONArray("data").toSearchKeywordItems())
                addAll(optJSONObject("data")?.optJSONArray("list").toSearchKeywordItems())
                addAll(optJSONObject("data")?.optJSONArray("items").toSearchKeywordItems())
                addAll(optJSONArray("list").toSearchKeywordItems())
                addAll(optJSONArray("items").toSearchKeywordItems())
                addAll(optJSONArray("keywords").toSearchKeywordItems())
                addAll(optJSONArray("hot").toSearchKeywordItems())
                addAll(optJSONArray("search").toSearchKeywordItems())
            }.filterNot { it.word.contains("广告") }
            if (items.isNotEmpty()) {
                resultGroups += TvSearchKeywordGroup(title = "推荐", items = items)
            }
        }

        return resultGroups
    }

    private fun JSONObject.extractSearchWords(): List<TvSearchKeywordItem> {
        val collected = linkedSetOf<TvSearchKeywordItem>()
        collected += optJSONArray("data").toSearchKeywordItems()
        collected += optJSONObject("data")?.optJSONArray("list").toSearchKeywordItems()
        collected += optJSONObject("data")?.optJSONArray("items").toSearchKeywordItems()
        collected += optJSONArray("list").toSearchKeywordItems()
        collected += optJSONArray("items").toSearchKeywordItems()
        collected += optJSONArray("keywords").toSearchKeywordItems()
        collected += optJSONArray("hot").toSearchKeywordItems()
        collected += optJSONArray("search").toSearchKeywordItems()
        return collected.toList()
    }

    private fun defaultSearchGroups(): List<TvSearchKeywordGroup> = listOf(
        TvSearchKeywordGroup(
            title = "热搜",
            items = listOf("最新", "热播", "高分", "电影", "电视剧", "综艺", "动漫", "短剧").map { TvSearchKeywordItem(it) },
        ),
    )

    private fun isPromotionalRecommendItem(item: JSONObject): Boolean {
        val rawId = item.optString("id").trim()
        val click = item.optString("click").trim()
        val title = item.optString("name")
            .ifBlank { item.optString("title") }
            .ifBlank { item.optString("vod_name") }
        val label = item.optString("label")
        val remark = item.optString("remarks")
        val category = item.optString("type_name")
        val overview = item.optString("highlight").ifBlank { item.optString("dynamic") }
        val poster = item.optString("cover")
            .ifBlank { item.optString("poster") }
            .ifBlank { item.optString("image") }
            .ifBlank { item.optString("vod_pic") }
        val combinedText = listOf(title, label, remark, category, overview).joinToString(" ")
        val suspiciousKeywords = listOf(
            "教育", "官网", "下载", "注册", "充值", "返利", "福利", "直播",
            "客服", "微信", "QQ", "qq群", "成人视频", "成人", "美女",
            "棋牌", "彩票", "赌场", "娱乐城", "体育投", "菠菜", "约炮",
            "赚钱", "副业", "兼职", "推广", "安装", "代理"
        )

        if (rawId.isBlank() && looksLikeExternalJump(click)) {
            return true
        }

        if (suspiciousKeywords.any { keyword -> combinedText.contains(keyword, ignoreCase = true) }) {
            return true
        }

        val lowerPoster = poster.lowercase()
        if (
            lowerPoster.contains("advert") ||
            lowerPoster.contains("promo") ||
            lowerPoster.contains("tuiguang") ||
            lowerPoster.contains("guanggao")
        ) {
            return true
        }

        return rawId.isBlank() &&
            category.isBlank() &&
            item.optString("year").isBlank() &&
            remark.isBlank() &&
            label.isBlank()
    }

    private fun looksLikeExternalJump(value: String): Boolean {
        if (value.isBlank()) return false
        val normalized = value.lowercase()
        return normalized.startsWith("http://") ||
            normalized.startsWith("https://") ||
            normalized.startsWith("intent://") ||
            normalized.contains("://") ||
            normalized.contains("?") ||
            normalized.contains("&") ||
            normalized.contains("/")
    }

    private inline fun <reified T : Throwable> Throwable?.findCause(): T? {
        var current = this
        while (current != null) {
            if (current is T) return current
            current = current.cause
        }
        return null
    }

    private companion object {
        const val TAG = "TvRepository"
        const val SEARCH_PAGE_SIZE = 30
    }

    private fun fallbackTopNav(): List<TvNavItem> = listOf(
        TvNavItem(id = 0, name = "推荐"),
        TvNavItem(id = 1, name = "电影"),
        TvNavItem(id = 2, name = "剧集"),
        TvNavItem(id = 3, name = "综艺"),
        TvNavItem(id = 4, name = "动漫"),
        TvNavItem(id = 36, name = "短剧"),
        TvNavItem(id = 26, name = "福利"),
    )

    private fun fallbackFilterGroups(sortValues: List<String> = fallbackSortValues()): List<TvFilterGroup> = listOf(
        TvFilterGroup(id = 1, name = "电影", sortValues = sortValues),
        TvFilterGroup(id = 2, name = "剧集", sortValues = sortValues),
        TvFilterGroup(id = 3, name = "综艺", sortValues = sortValues),
        TvFilterGroup(id = 4, name = "动漫", sortValues = sortValues),
        TvFilterGroup(id = 36, name = "短剧", sortValues = sortValues),
        TvFilterGroup(id = 26, name = "福利", sortValues = sortValues),
    )

    private fun fallbackSortValues(): List<String> = listOf(
        "by_default",
        "by_time",
        "by_hits",
        "by_score",
    )

    private suspend fun resolveEpisodeUrlInternal(episode: TvEpisode, forceParse: Boolean = false): String {
        val playUrl = episode.playUrl.trim()
        val parseUrl = episode.parseUrl.trim()

        Log.d(
            TAG,
            "resolve episode input: episode=${episode.name} readyToPlay=${episode.readyToPlay} forceParse=$forceParse playUrl=$playUrl parseUrl=$parseUrl",
        )

        if (!forceParse && parseUrl.isNotBlank() && looksLikePlayableUrl(parseUrl)) {
            val resolved = normalizeMediaUrl(parseUrl)
            Log.d(TAG, "resolve episode output: source=parseUrl resolved=$resolved")
            return resolved
        }

        val shouldParse = forceParse || playUrl.startsWith("parse_") || !looksLikeDirectNetworkUrl(playUrl)
        if (!shouldParse) {
            val resolved = normalizeMediaUrl(playUrl)
            Log.d(TAG, "resolve episode output: source=playUrl resolved=$resolved")
            return resolved
        }

        if (playUrl.isBlank()) {
            return ""
        }

        return try {
            val response = api.get(
                "/movie_addr/parse_url",
                mapOf(
                    "type" to "play",
                    "episode_id" to episode.episodeId,
                    "from_code" to episode.fromCode,
                    "play_url" to playUrl,
                    "refresh" to 1,
                ),
            )
            val data = response.optJSONObject("data") ?: response
            val message = response.optString("msg")
                .ifBlank { response.optString("message") }
                .ifBlank { response.optString("error") }

            if (response.optInt("errorCode", 0) == 1015 || message.contains("无需")) {
                return normalizeMediaUrl(playUrl)
            }

            val candidate = listOf(
                data.optString("play_url"),
                data.optString("download_url"),
                data.optJSONObject("data")?.optString("play_url").orEmpty(),
                data.optString("url"),
                response.optString("play_url"),
                response.optString("url"),
            ).firstOrNull { it.isNotBlank() }.orEmpty()

            if (candidate.isNotBlank()) {
                val resolved = normalizeMediaUrl(candidate)
                Log.d(TAG, "resolve episode output: source=parse_api candidate=$candidate resolved=$resolved")
                resolved
            } else {
                val resolved = normalizeMediaUrl(playUrl)
                Log.d(TAG, "resolve episode output: source=parse_api fallback resolved=$resolved")
                resolved
            }
        } catch (throwable: Throwable) {
            Log.w(TAG, "resolve episode url failed", throwable)
            val resolved = normalizeMediaUrl(playUrl)
            Log.d(TAG, "resolve episode output: source=error fallback resolved=$resolved")
            resolved
        }
    }

    private fun looksLikePlayableUrl(value: String): Boolean {
        val normalized = value.trim()
        if (normalized.isBlank()) return false
        val lower = normalized.lowercase()
        return lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("blob:") ||
            lower.contains(".m3u8") ||
            lower.contains(".mp4") ||
            lower.contains(".flv") ||
            lower.contains(".mkv")
    }

    private fun looksLikeDirectNetworkUrl(value: String): Boolean {
        val normalized = value.trim()
        if (normalized.isBlank() || normalized.startsWith("parse_")) return false
        val lower = normalized.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    private fun normalizeMediaUrl(value: String): String {
        return when {
            value.isBlank() -> ""
            value.startsWith("//") -> "https:$value"
            else -> value
        }
    }
}

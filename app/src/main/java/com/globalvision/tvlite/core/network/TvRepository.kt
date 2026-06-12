package com.globalvision.tvlite.core.network

import android.util.Log
import com.globalvision.tvlite.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.CertificateNotYetValidException
import java.util.concurrent.ConcurrentHashMap

class TvRepository(
    private val api: TvApiClient = TvApiClient(),
) {
    @Volatile
    private var homeFeedCache: TvHomeFeed? = null
    @Volatile
    private var homeFeedDiagnostic: HomeFeedDiagnostic? = null
    @Volatile
    private var searchRankingCache: List<TvSearchKeywordGroup>? = null
    @Volatile
    private var searchLatelyWordsCache: List<TvSearchKeywordItem>? = null

    private val screenMoviesPageCache = ConcurrentHashMap<ScreenMoviesQuery, ConcurrentHashMap<Int, List<TvPosterItem>>>()
    private val screenMoviesStateCache = ConcurrentHashMap<ScreenMoviesQuery, ScreenMoviesState>()

    suspend fun getHomeFeed(): TvHomeFeed = withContext(Dispatchers.IO) {
        homeFeedCache?.let {
            Log.d(TAG, "home feed cache hit")
            return@withContext it
        }
        val config = try {
            fetchAppConfig()
        } catch (throwable: Throwable) {
            Log.e(TAG, "app config request failed", throwable)
            emptyConfig()
        }
        var recommendFailure: Throwable? = null
        val recommend = try {
            api.get("/movie/index_recommend")
        } catch (throwable: Throwable) {
            recommendFailure = throwable
            Log.e(TAG, "home recommend request failed", throwable)
            null
        }
        if (recommend == null) {
            Log.w(TAG, "home recommend request failed, using sample feed")
            homeFeedDiagnostic = buildHomeFeedDiagnostic(recommendFailure)
            return@withContext sampleHomeFeed(config).also {
                homeFeedCache = it
            }
        }
        val feed = mapHomeFeed(config, recommend)
        homeFeedDiagnostic = null
        Log.d(TAG, "home feed loaded: nav=${feed.config.topNav.size}, banners=${feed.banners.size}, sections=${feed.sections.size}")
        homeFeedCache = feed
        feed
    }

    fun peekHomeFeed(): TvHomeFeed? = homeFeedCache
    fun peekHomeFeedDiagnostic(): HomeFeedDiagnostic? = homeFeedDiagnostic

    suspend fun search(keyword: String, page: Int = 1): TvSearchResult = withContext(Dispatchers.IO) {
        val response = api.get("/movie/search", mapOf("keyword" to keyword, "page" to page, "res_type" to "by_movie_name"))
        val data = response.optJSONObject("data") ?: JSONObject()
        val result = TvSearchResult(
            items = data.optJSONArray("list").toPosterItems(),
            total = data.optInt("total", 0),
        )
        Log.d(TAG, "search loaded: keyword=$keyword page=$page total=${result.total} items=${result.items.size}")
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
        sort: String = "by_time",
        classValue: String? = null,
        area: String? = null,
        year: String? = null,
        page: Int = 1,
        pageSize: Int = 30,
    ): List<TvPosterItem> = withContext(Dispatchers.IO) {
        val query = ScreenMoviesQuery(
            typeId = typeId,
            sort = sort,
            classValue = classValue.orEmpty(),
            area = area.orEmpty(),
            year = year.orEmpty(),
        )
        screenMoviesPageCache[query]?.get(page)?.let {
            Log.d(TAG, "screen list cache hit: typeId=$typeId sort=$sort class=${classValue.orEmpty()} area=${area.orEmpty()} year=${year.orEmpty()} page=$page items=${it.size}")
            return@withContext it
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
        val pageCache = screenMoviesPageCache.getOrPut(query) { ConcurrentHashMap() }
        pageCache[page] = list
        val nextPage = page + 1
        val hasMore = list.size >= pageSize
        val currentState = screenMoviesStateCache[query]
        screenMoviesStateCache[query] = if (page == 1 || currentState == null) {
            ScreenMoviesState(
                items = list,
                nextPage = nextPage,
                hasMore = hasMore,
            )
        } else {
            ScreenMoviesState(
                items = currentState.items + list,
                nextPage = nextPage,
                hasMore = hasMore,
            )
        }
        Log.d(
            TAG,
            "screen list loaded: typeId=$typeId sort=$sort class=${classValue.orEmpty()} area=${area.orEmpty()} year=${year.orEmpty()} items=${list.size}",
        )
        list
    }

    fun peekScreenMoviesState(
        typeId: Int,
        sort: String = "by_time",
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
        sort: String = "by_time",
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
        detail
    }

    suspend fun getEpisodes(movieId: String, fromCode: String): List<TvEpisode> = withContext(Dispatchers.IO) {
        val response = api.get("/movie_addr/list", mapOf("movie_id" to movieId, "from_code" to fromCode))
        response.optJSONArray("data").toEpisodes(fromCode)
    }

    suspend fun resolveEpisodeUrl(episode: TvEpisode): String = withContext(Dispatchers.IO) {
        resolveEpisodeUrlInternal(episode)
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

    private fun emptyConfig(): TvAppConfig = TvAppConfig()

    data class HomeFeedDiagnostic(
        val title: String,
        val message: String,
        val isUsingSampleData: Boolean = false,
    )

    data class ScreenMoviesState(
        val items: List<TvPosterItem>,
        val nextPage: Int,
        val hasMore: Boolean,
    )

    private data class ScreenMoviesQuery(
        val typeId: Int,
        val sort: String,
        val classValue: String,
        val area: String,
        val year: String,
    )

    private fun mapConfig(payload: JSONObject): TvAppConfig {
        val nav = payload.optJSONArray("index_top_nav").toNavItems()
        val filterGroups = payload.optJSONObject("movie_screen")
            ?.optJSONArray("filter")
            .toFilterGroups()
        return TvAppConfig(
            topNav = nav,
            filterGroups = filterGroups,
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
            value.startsWith("http://") -> value.replace("http://", "https://")
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

    private fun JSONArray?.toFilterGroups(): List<TvFilterGroup> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = optJSONObject(i) ?: continue
                add(
                    TvFilterGroup(
                        id = item.optInt("id"),
                        name = item.optString("name"),
                        classValues = item.optJSONArray("class").toStringList(),
                        areaValues = item.optJSONArray("area").toStringList(),
                        yearValues = item.optJSONArray("year").toStringList(),
                        sortValues = item.optJSONArray("sort").toStringList(),
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
                add(optString(i))
            }
        }.filter { it.isNotBlank() }
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

    private fun buildHomeFeedDiagnostic(throwable: Throwable?): HomeFeedDiagnostic {
        if (throwable.findCause<CertificateNotYetValidException>() != null) {
            return HomeFeedDiagnostic(
                title = "当前显示演示数据",
                message = "电视模拟器系统时间偏早，HTTPS 证书尚未生效。请在模拟器中同步日期时间后重新打开应用。",
                isUsingSampleData = true,
            )
        }

        if (throwable.findCause<javax.net.ssl.SSLHandshakeException>() != null) {
            return HomeFeedDiagnostic(
                title = "当前显示演示数据",
                message = "首页接口的 HTTPS 校验失败。请优先检查电视模拟器的系统时间和证书链状态。",
                isUsingSampleData = true,
            )
        }

        return HomeFeedDiagnostic(
            title = "当前显示演示数据",
            message = "首页接口暂时不可用，应用已切换到本地演示内容。",
            isUsingSampleData = true,
        )
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
    }

    private suspend fun resolveEpisodeUrlInternal(episode: TvEpisode): String {
        val playUrl = episode.playUrl.trim()
        val parseUrl = episode.parseUrl.trim()

        Log.d(
            TAG,
            "resolve episode input: episode=${episode.name} readyToPlay=${episode.readyToPlay} playUrl=$playUrl parseUrl=$parseUrl",
        )

        if (parseUrl.isNotBlank()) {
            val resolved = normalizeMediaUrl(parseUrl)
            Log.d(TAG, "resolve episode output: source=parseUrl resolved=$resolved")
            return resolved
        }

        val shouldParse = !episode.readyToPlay || playUrl.startsWith("parse_") || !looksLikePlayableUrl(playUrl)
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

    private fun normalizeMediaUrl(value: String): String {
        return when {
            value.isBlank() -> ""
            value.startsWith("//") -> "https:$value"
            else -> value
        }
    }
}

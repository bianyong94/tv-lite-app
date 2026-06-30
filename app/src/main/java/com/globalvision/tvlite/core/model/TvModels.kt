package com.globalvision.tvlite.core.model

data class TvNavItem(
    val id: Int,
    val name: String,
)

data class TvFilterGroup(
    val id: Int,
    val name: String,
    val classValues: List<String> = emptyList(),
    val areaValues: List<String> = emptyList(),
    val yearValues: List<String> = emptyList(),
    val sortValues: List<String> = emptyList(),
)

data class TvAppConfig(
    val topNav: List<TvNavItem> = emptyList(),
    val filterGroups: List<TvFilterGroup> = emptyList(),
)

data class TvPosterItem(
    val id: String,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String = "",
    val label: String = "",
    val year: String = "",
    val score: String = "",
    val category: String = "",
    val remark: String = "",
    val overview: String = "",
)

data class TvHomeSection(
    val title: String,
    val items: List<TvPosterItem>,
)

data class TvHomeFeed(
    val config: TvAppConfig,
    val banners: List<TvPosterItem>,
    val sections: List<TvHomeSection>,
)

data class TvMovieTopic(
    val id: String,
    val title: String,
    val coverUrl: String = "",
    val description: String = "",
    val movieCount: Int = 0,
    val viewCount: String = "",
    val items: List<TvPosterItem> = emptyList(),
)

data class TvMovieRanking(
    val id: String,
    val title: String,
    val items: List<TvPosterItem> = emptyList(),
)

data class TvEpisode(
    val episodeId: Int,
    val name: String,
    val playUrl: String,
    val fromCode: String,
    val parseUrl: String = "",
    val readyToPlay: Boolean = false,
)

data class TvPlaySource(
    val code: String,
    val name: String,
    val episodes: List<TvEpisode>,
)

data class TvMovieDetail(
    val id: String,
    val title: String,
    val posterUrl: String,
    val year: String = "",
    val area: String = "",
    val director: String = "",
    val writer: String = "",
    val actor: String = "",
    val content: String = "",
    val remarks: String = "",
    val sources: List<TvPlaySource> = emptyList(),
)

data class TvPlaybackHistoryEntry(
    val movieId: String,
    val title: String,
    val posterUrl: String,
    val year: String = "",
    val category: String = "",
    val remark: String = "",
    val sourceIndex: Int = 0,
    val sourceName: String = "",
    val episodeIndex: Int = 0,
    val episodeName: String = "",
    val positionMs: Long = 0L,
    val updatedAt: Long = 0L,
)

data class TvSearchResult(
    val items: List<TvPosterItem>,
    val page: Int = 1,
    val pageSize: Int = 0,
    val total: Int = 0,
)

data class TvSearchKeywordItem(
    val word: String,
    val hot: Int? = null,
)

data class TvSearchKeywordGroup(
    val title: String,
    val items: List<TvSearchKeywordItem>,
)

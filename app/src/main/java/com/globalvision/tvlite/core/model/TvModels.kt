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

data class TvSearchResult(
    val items: List<TvPosterItem>,
    val total: Int = 0,
)

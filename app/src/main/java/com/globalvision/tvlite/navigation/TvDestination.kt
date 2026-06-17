package com.globalvision.tvlite.navigation

sealed class TvDestination(val route: String) {
    data object Home : TvDestination("home")
    data object Search : TvDestination("search")
    data object History : TvDestination("history")
    data object Detail : TvDestination("detail/{id}?sourceIndex={sourceIndex}&episodeIndex={episodeIndex}&positionMs={positionMs}") {
        fun createRoute(
            id: String,
            sourceIndex: Int = 0,
            episodeIndex: Int = 0,
            positionMs: Long = 0L,
        ) = "detail/$id?sourceIndex=$sourceIndex&episodeIndex=$episodeIndex&positionMs=$positionMs"
    }

    data object Player : TvDestination("player?title={title}&movieId={movieId}&sourceIndex={sourceIndex}&episodeIndex={episodeIndex}&positionMs={positionMs}") {
        fun createRoute(
            title: String,
            movieId: String,
            sourceIndex: Int,
            episodeIndex: Int,
            positionMs: Long = 0L,
        ) =
            "player?title=$title&movieId=$movieId&sourceIndex=$sourceIndex&episodeIndex=$episodeIndex&positionMs=$positionMs"
    }
}

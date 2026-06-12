package com.globalvision.tvlite.navigation

sealed class TvDestination(val route: String) {
    data object Home : TvDestination("home")
    data object Search : TvDestination("search")
    data object Detail : TvDestination("detail/{id}") {
        fun createRoute(id: String) = "detail/$id"
    }

    data object Player : TvDestination("player?title={title}&movieId={movieId}&sourceIndex={sourceIndex}&episodeIndex={episodeIndex}") {
        fun createRoute(
            title: String,
            movieId: String,
            sourceIndex: Int,
            episodeIndex: Int,
        ) =
            "player?title=$title&movieId=$movieId&sourceIndex=$sourceIndex&episodeIndex=$episodeIndex"
    }
}

package com.globalvision.tvlite.navigation

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavHostController
import androidx.media3.common.util.UnstableApi
import com.globalvision.tvlite.core.model.TvPlaybackHistoryEntry
import com.globalvision.tvlite.core.network.TvRepository
import com.globalvision.tvlite.feature.detail.DetailScreen
import com.globalvision.tvlite.feature.history.HistoryScreen
import com.globalvision.tvlite.feature.home.HomeScreen
import com.globalvision.tvlite.feature.player.PlayerScreen
import com.globalvision.tvlite.feature.search.SearchScreen

@UnstableApi
@Composable
fun TvNavGraph(
    navController: NavHostController,
    repository: TvRepository,
) {
    val tag = "TvNavGraph"
    NavHost(
        navController = navController,
        startDestination = TvDestination.Home.route,
    ) {
        composable(TvDestination.Home.route) {
            HomeScreen(
                repository = repository,
                onSearch = {
                    Log.d(tag, "navigate search")
                    navController.navigate(TvDestination.Search.route)
                },
                onHistory = {
                    Log.d(tag, "navigate history")
                    navController.navigate(TvDestination.History.route)
                },
                onOpenDetail = {
                    Log.d(tag, "navigate detail: id=$it")
                    navController.navigate(TvDestination.Detail.createRoute(it))
                },
            )
        }

        composable(TvDestination.History.route) {
            HistoryScreen(
                onBack = {
                    Log.d(tag, "back from history")
                    navController.popBackStack()
                },
                onOpenDetail = { historyEntry: TvPlaybackHistoryEntry ->
                    navController.navigate(
                        TvDestination.Detail.createRoute(
                            id = historyEntry.movieId,
                            sourceIndex = historyEntry.sourceIndex,
                            episodeIndex = historyEntry.episodeIndex,
                            positionMs = historyEntry.positionMs,
                        ),
                    )
                },
            )
        }

        composable(TvDestination.Search.route) {
            SearchScreen(
                repository = repository,
                onBack = {
                    Log.d(tag, "back from search")
                    navController.popBackStack()
                },
                onOpenDetail = {
                    Log.d(tag, "navigate detail from search: id=$it")
                    navController.navigate(TvDestination.Detail.createRoute(it))
                },
            )
        }

        composable(
            route = TvDestination.Detail.route,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("sourceIndex") { type = NavType.IntType; defaultValue = 0 },
                navArgument("episodeIndex") { type = NavType.IntType; defaultValue = 0 },
                navArgument("positionMs") { type = NavType.LongType; defaultValue = 0L },
            ),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            DetailScreen(
                repository = repository,
                movieId = id,
                initialSourceIndex = entry.arguments?.getInt("sourceIndex") ?: 0,
                initialEpisodeIndex = entry.arguments?.getInt("episodeIndex") ?: 0,
                initialPositionMs = entry.arguments?.getLong("positionMs") ?: 0L,
                onBack = {
                    Log.d(tag, "back from detail: id=$id")
                    navController.popBackStack()
                },
                onPlay = { title, movieId, sourceIndex, episodeIndex, positionMs ->
                    Log.d(
                        tag,
                        "navigate player: title=$title movieId=$movieId sourceIndex=$sourceIndex episodeIndex=$episodeIndex positionMs=$positionMs",
                    )
                    navController.navigate(
                        TvDestination.Player.createRoute(
                            title = Uri.encode(title),
                            movieId = Uri.encode(movieId),
                            sourceIndex = sourceIndex,
                            episodeIndex = episodeIndex,
                            positionMs = positionMs,
                        ),
                    )
                },
            )
        }

        composable(
            route = TvDestination.Player.route,
            arguments = listOf(
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("movieId") { type = NavType.StringType; defaultValue = "" },
                navArgument("sourceIndex") { type = NavType.IntType; defaultValue = 0 },
                navArgument("episodeIndex") { type = NavType.IntType; defaultValue = 0 },
                navArgument("positionMs") { type = NavType.LongType; defaultValue = 0L },
            ),
        ) { entry ->
            PlayerScreen(
                repository = repository,
                title = Uri.decode(entry.arguments?.getString("title").orEmpty()),
                movieId = Uri.decode(entry.arguments?.getString("movieId").orEmpty()),
                sourceIndex = entry.arguments?.getInt("sourceIndex") ?: 0,
                episodeIndex = entry.arguments?.getInt("episodeIndex") ?: 0,
                initialPositionMs = entry.arguments?.getLong("positionMs") ?: 0L,
                onBack = {
                    Log.d(tag, "back from player")
                    navController.popBackStack()
                },
            )
        }
    }
}

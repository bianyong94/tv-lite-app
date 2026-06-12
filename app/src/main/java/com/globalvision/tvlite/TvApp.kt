package com.globalvision.tvlite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import com.globalvision.tvlite.core.network.TvRepository
import com.globalvision.tvlite.feature.common.LocalTvStatusHostState
import com.globalvision.tvlite.navigation.TvNavGraph

@UnstableApi
@Composable
fun TvApp() {
    val navController = rememberNavController()
    val repository = remember { TvRepository() }

    CompositionLocalProvider(LocalTvStatusHostState provides null) {
        TvNavGraph(
            navController = navController,
            repository = repository,
        )
    }
}

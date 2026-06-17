package com.globalvision.tvlite.feature.common

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.globalvision.tvlite.core.network.TvRepository
import kotlinx.coroutines.launch

@Composable
fun TvPosterImage(
    posterUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    movieId: String? = null,
    repository: TvRepository? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val scope = rememberCoroutineScope()
    var modelUrl by remember(movieId, posterUrl) { mutableStateOf(posterUrl.takeIf { it.isNotBlank() }) }
    var fallbackTried by remember(movieId, posterUrl) { mutableStateOf(false) }

    AsyncImage(
        model = modelUrl,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        onError = { state ->
            Log.w(
                TAG,
                "poster load failed: movieId=${movieId.orEmpty()} url=${modelUrl.orEmpty()} throwable=${state.result.throwable.message}",
            )
            if (fallbackTried || movieId.isNullOrBlank() || repository == null) return@AsyncImage
            fallbackTried = true
            scope.launch {
                val fallbackUrl = repository.getPosterFallbackUrl(movieId)
                if (fallbackUrl.isNotBlank() && fallbackUrl != modelUrl) {
                    Log.d(TAG, "poster fallback hit: movieId=$movieId fallbackUrl=$fallbackUrl")
                    modelUrl = fallbackUrl
                }
            }
        },
    )
}

private const val TAG = "TvPosterImage"

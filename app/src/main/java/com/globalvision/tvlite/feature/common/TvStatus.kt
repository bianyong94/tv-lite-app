package com.globalvision.tvlite.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.globalvision.tvlite.ui.theme.TvDivider
import kotlinx.coroutines.delay

data class TvStatusMessage(
    val title: String,
    val detail: String,
    val timeoutMs: Long = 2200L,
)

class TvStatusHostState(
    private val state: MutableState<TvStatusMessage?>,
) {
    val current: TvStatusMessage?
        get() = state.value

    fun show(title: String, detail: String, timeoutMs: Long = 2200L) {
        state.value = TvStatusMessage(title = title, detail = detail, timeoutMs = timeoutMs)
    }

    fun clear() {
        state.value = null
    }
}

val LocalTvStatusHostState = compositionLocalOf<TvStatusHostState?> { null }

@Composable
fun rememberTvStatusHostState(): TvStatusHostState {
    val state = remember { mutableStateOf<TvStatusMessage?>(null) }
    return remember(state) { TvStatusHostState(state) }
}

@Composable
fun TvStatusHost(
    hostState: TvStatusHostState,
    modifier: Modifier = Modifier,
) {
    val current = hostState.current ?: return

    LaunchedEffect(current) {
        delay(current.timeoutMs)
        if (hostState.current == current) {
            hostState.clear()
        }
    }

    Box(
        modifier = modifier
            .widthIn(max = 560.dp)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    ),
                ),
                shape = RoundedCornerShape(24.dp),
            )
            .border(1.dp, TvDivider, RoundedCornerShape(24.dp))
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = current.title,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = current.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

package com.globalvision.tvlite.feature.common

import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.globalvision.tvlite.ui.theme.TvFocusBorder
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch

fun Modifier.tvFocusBorder(
    focused: Boolean,
    shape: Shape,
    width: Dp = 2.5.dp,
    unfocusedWidth: Dp = 0.dp,
    unfocusedColor: Color = Color.Transparent,
): Modifier = border(
    width = if (focused) width else unfocusedWidth,
    color = if (focused) TvFocusBorder else unfocusedColor,
    shape = shape,
)

@Composable
fun Modifier.ensureVisibleOnFocus(): Modifier {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val visibilityTolerance = 24.dp
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    return this
        .bringIntoViewRequester(bringIntoViewRequester)
        .onGloballyPositioned { coordinates = it }
        .onFocusChanged { focusState ->
            if (focusState.isFocused) {
                coroutineScope.launch {
                    awaitFrame()
                    val layoutCoordinates: LayoutCoordinates = coordinates ?: return@launch
                    if (!layoutCoordinates.isAttached) return@launch

                    val bounds = layoutCoordinates.boundsInRoot()
                    var rootCoordinates: LayoutCoordinates = layoutCoordinates
                    while (true) {
                        val parentCoordinates = rootCoordinates.parentLayoutCoordinates ?: break
                        rootCoordinates = parentCoordinates
                    }
                    val rootSize = rootCoordinates.size
                    val tolerancePx = with(density) { visibilityTolerance.toPx() }
                    val visibleBounds = Rect(
                        left = -tolerancePx,
                        top = -tolerancePx,
                        right = rootSize.width.toFloat() + tolerancePx,
                        bottom = rootSize.height.toFloat() + tolerancePx,
                    )

                    val alreadyVisible =
                        bounds.left >= visibleBounds.left &&
                            bounds.top >= visibleBounds.top &&
                            bounds.right <= visibleBounds.right &&
                            bounds.bottom <= visibleBounds.bottom

                    if (!alreadyVisible) {
                        bringIntoViewRequester.bringIntoView()
                    }
                }
            }
        }
}

package com.globalvision.tvlite.feature.common

import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.globalvision.tvlite.ui.theme.TvFocusBorder
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

    return this
        .bringIntoViewRequester(bringIntoViewRequester)
        .onFocusChanged { focusState ->
            if (focusState.isFocused) {
                coroutineScope.launch {
                    bringIntoViewRequester.bringIntoView()
                }
            }
        }
}

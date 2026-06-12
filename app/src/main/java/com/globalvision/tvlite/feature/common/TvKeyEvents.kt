package com.globalvision.tvlite.feature.common

import android.os.SystemClock
import java.util.HashMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

private const val DPAD_THROTTLE_WINDOW_MS = 90L

private object TvDpadThrottle {
    private val lastInitialEventAtMs = HashMap<Key, Long>()

    fun shouldConsume(keyEvent: KeyEvent): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false
        val key = keyEvent.key
        val isDirectionalKey = key == Key.DirectionUp ||
            key == Key.DirectionDown ||
            key == Key.DirectionLeft ||
            key == Key.DirectionRight
        val isConfirmKey = key == Key.DirectionCenter || key == Key.Enter
        if (!isDirectionalKey && !isConfirmKey) return false

        if (isDirectionalKey && keyEvent.nativeKeyEvent.repeatCount > 0) {
            return false
        }

        val now = SystemClock.uptimeMillis()
        val lastEventAtMs = lastInitialEventAtMs[key]
        val consume = lastEventAtMs != null && now - lastEventAtMs in 1 until DPAD_THROTTLE_WINDOW_MS
        lastInitialEventAtMs[key] = now
        return consume
    }
}

fun KeyEvent.isRepeatedDpadEvent(): Boolean {
    return TvDpadThrottle.shouldConsume(this)
}

fun Modifier.consumeRepeatedDpadEvents(): Modifier =
    onPreviewKeyEvent { event -> event.isRepeatedDpadEvent() }

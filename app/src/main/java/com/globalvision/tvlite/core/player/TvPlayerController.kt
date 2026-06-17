package com.globalvision.tvlite.core.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class TvPlayerController(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    fun play(url: String) {
        if (url.isBlank()) return
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
        player.prepare()
        player.playWhenReady = true
    }

    fun togglePlayPause() {
        player.playWhenReady = !player.playWhenReady
        if (player.playWhenReady) player.play() else player.pause()
    }

    fun play() {
        player.playWhenReady = true
        player.play()
    }

    fun pause() {
        player.playWhenReady = false
        player.pause()
    }

    fun seekBy(offsetMs: Long) {
        val duration = durationMs()
        val target = (player.currentPosition + offsetMs)
            .coerceAtLeast(0L)
            .let { if (duration > 0L) it.coerceAtMost(duration) else it }
        player.seekTo(target)
    }

    fun seekTo(positionMs: Long) {
        val duration = durationMs()
        val target = positionMs
            .coerceAtLeast(0L)
            .let { if (duration > 0L) it.coerceAtMost(duration) else it }
        player.seekTo(target)
    }

    fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackParameters(PlaybackParameters(speed))
    }

    fun playbackSpeed(): Float = player.playbackParameters.speed

    fun isPlaying(): Boolean = player.isPlaying

    fun currentPositionMs(): Long = player.currentPosition

    fun durationMs(): Long = player.duration.takeIf { it > 0 } ?: 0L

    fun bufferedPositionMs(): Long = player.bufferedPosition

    fun addListener(listener: Player.Listener) {
        player.addListener(listener)
    }

    fun removeListener(listener: Player.Listener) {
        player.removeListener(listener)
    }

    fun release() {
        player.release()
    }
}

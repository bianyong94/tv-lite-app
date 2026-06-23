package com.globalvision.tvlite.core.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

@UnstableApi
class TvPlayerController(context: Context) {
    private val trackSelector = DefaultTrackSelector(context).apply {
        setParameters(
            buildUponParameters()
                .setMaxVideoBitrate(STARTUP_MAX_VIDEO_BITRATE)
        )
    }
    private var startupQualityLimitEnabled = true
    private var manualVideoTrackSelectionEnabled = false

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setTrackSelector(trackSelector)
        .setLoadControl(buildLoadControl())
        .setMediaSourceFactory(buildMediaSourceFactory(context))
        .build()
        .apply {
            setSeekParameters(SeekParameters.CLOSEST_SYNC)
        }

    fun play(url: String) {
        if (url.isBlank()) return
        enableStartupQualityLimit()
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
        player.prepare()
        player.playWhenReady = true
    }

    fun allowAdaptiveVideoQuality() {
        if (!startupQualityLimitEnabled || manualVideoTrackSelectionEnabled) return
        startupQualityLimitEnabled = false
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .clearVideoSizeConstraints()
                .setMaxVideoBitrate(Int.MAX_VALUE)
        )
    }

    fun enableAutoVideoQuality() {
        manualVideoTrackSelectionEnabled = false
        startupQualityLimitEnabled = false
        player.setTrackSelectionParameters(
            player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_VIDEO)
                .clearVideoSizeConstraints()
                .setMaxVideoBitrate(Int.MAX_VALUE)
                .build(),
        )
    }

    fun selectVideoTrack(trackGroup: TrackGroup, trackIndex: Int) {
        manualVideoTrackSelectionEnabled = true
        startupQualityLimitEnabled = false
        player.setTrackSelectionParameters(
            player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_VIDEO)
                .clearVideoSizeConstraints()
                .setMaxVideoBitrate(Int.MAX_VALUE)
                .addOverride(TrackSelectionOverride(trackGroup, trackIndex))
                .build(),
        )
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

    private fun enableStartupQualityLimit() {
        manualVideoTrackSelectionEnabled = false
        startupQualityLimitEnabled = true
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setMaxVideoBitrate(STARTUP_MAX_VIDEO_BITRATE)
        )
    }

    private companion object {
        private const val STARTUP_MAX_VIDEO_BITRATE = 2_500_000

        private fun buildLoadControl(): DefaultLoadControl {
            return DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    8_000,
                    45_000,
                    700,
                    1_200,
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        }

        private fun buildMediaSourceFactory(context: Context): DefaultMediaSourceFactory {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(
                    "Mozilla/5.0 (Linux; Android 13; Android TV) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/125.0.0.0 Safari/537.36"
                )
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(25_000)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(
                    mapOf(
                        "Accept" to "*/*",
                        "Origin" to "https://bycurry.cc",
                        "Referer" to "https://bycurry.cc/",
                    )
                )
            return DefaultMediaSourceFactory(context)
                .setDataSourceFactory(httpDataSourceFactory)
        }
    }
}

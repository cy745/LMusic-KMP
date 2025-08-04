package com.lalilu.lplayer.player

import co.touchlab.kermit.Logger
import com.lalilu.lplayer.playback.PlayBackWithQueue
import jdk.internal.util.OperatingSystem.isMacOS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent

object VLCPlayer : PlayBackWithQueue() {
    private var mediaPlayer: MediaPlayer? = null
    private val currentDuration = MutableStateFlow<Long>(0)
    private val currentPosition = MutableStateFlow<Long>(0)
    private val isPlaying = MutableStateFlow(false)

    init {
        System.setProperty("vlcj.log", "DEBUG")
    }

    private fun initMediaPlayer(): Boolean {
        try {
            NativeDiscovery().discover()

            val component = if (isMacOS()) CallbackMediaPlayerComponent() else EmbeddedMediaPlayerComponent()
            mediaPlayer = component.mediaPlayerFactory().mediaPlayers().newMediaPlayer()


            mediaPlayer?.events()?.addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                override fun mediaPlayerReady(mediaPlayer: MediaPlayer?) {
                }

                override fun finished(mediaPlayer: MediaPlayer?) {
                }

                override fun error(mediaPlayer: MediaPlayer?) {
                }

                override fun playing(mediaPlayer: MediaPlayer?) {
                }

                override fun paused(mediaPlayer: MediaPlayer?) {
                }

                override fun buffering(mediaPlayer: MediaPlayer?, newCache: Float) {
                }
            })
            return true
        } catch (e: Exception) {
            Logger.e("Failed to initialize media player: ${e.message}")
            return false
        }
    }

    override fun currentDuration(): Long = currentDuration.value
    override fun currentDurationFlow(): StateFlow<Long> = currentDuration
    override fun currentPosition(): Long = currentPosition.value
    override fun currentPositionFlow(): StateFlow<Long> = currentPosition
    override fun isPlaying(): Boolean = isPlaying.value
    override fun isPlayingFlow(): StateFlow<Boolean> = isPlaying
}
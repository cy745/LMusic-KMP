package com.lalilu.lplayer.player

import co.touchlab.kermit.Logger
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import java.util.*

object VLCPlayer {
    private var mediaPlayer: MediaPlayer? = null

    init {
        System.setProperty("vlcj.log", "DEBUG")
    }

    fun getPlayer(): MediaPlayer {
        return mediaPlayer ?: initMediaPlayer()
            .also { mediaPlayer = it }
    }

    fun releaseMediaPlayer() {
        mediaPlayer?.controls()?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun initMediaPlayer(): MediaPlayer {
        try {
            NativeDiscovery().discover()
        } catch (e: Exception) {
            Logger.e(tag = "VLCPlayer", messageString = "Failed to discover native libraries, Retry", throwable = e)
        }

        val component = if (isMacOS()) CallbackMediaPlayerComponent() else EmbeddedMediaPlayerComponent()
        val player = component.mediaPlayerFactory().mediaPlayers().newMediaPlayer()
        requireNotNull(player) { "Media player is null" }

        return player
    }

    private fun isMacOS(): Boolean {
        val os = System.getProperty("os.name", "generic").lowercase(Locale.ENGLISH)
        return os.contains("mac") || os.contains("darwin")
    }
}
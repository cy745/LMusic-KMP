package com.lalilu.lplayer.player

import uk.co.caprica.vlcj.player.base.MediaPlayer

object VLCPlayer {
    private val audioFactory by lazy { uk.co.caprica.vlcj.factory.MediaPlayerFactory("--no-video") }
    /** Fresh decoder ownership for a bounded PCM window; no second playback policy. */
    fun newAudioPlayer(): MediaPlayer = audioFactory.mediaPlayers().newMediaPlayer()
    private var mediaPlayer: MediaPlayer? = null

    fun getPlayer(): MediaPlayer? {
        if (!VLCPlayerLoader.isReady.value) throw IllegalStateException("VLC not ready")
        return mediaPlayer ?: initMediaPlayer()
            .also { mediaPlayer = it }
    }

    fun releaseMediaPlayer() {
        mediaPlayer?.controls()?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun initMediaPlayer(): MediaPlayer {
        val player = newAudioPlayer()
        requireNotNull(player) { "Media player is null" }

        return player
    }

}

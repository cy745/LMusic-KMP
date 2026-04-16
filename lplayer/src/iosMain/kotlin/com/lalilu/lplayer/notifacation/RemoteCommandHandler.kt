package com.lalilu.lplayer.notifacation

import io.github.oshai.kotlinlogging.KotlinLogging
import com.lalilu.common.ext.io
import com.lalilu.lplayer.playback.Playback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.MediaPlayer.*
import kotlin.coroutines.CoroutineContext

object RemoteCommandHandler : CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.io
    private val remoteCommandCenter = MPRemoteCommandCenter.sharedCommandCenter()
    private val logger = KotlinLogging.logger("RemoteCommandHandler")
    fun debugLog(message: String) = logger.info { message }


    fun bindPlayback(playback: Playback) {
        remoteCommandCenter.playCommand.setEnabled(true)
        remoteCommandCenter.playCommand.addTargetWithHandler { event: MPRemoteCommandEvent? ->
            debugLog("playCommand")
            if (!playback.isPlaying.value) {
                launch { playback.play() }
                MPRemoteCommandHandlerStatusSuccess
            } else {
                MPRemoteCommandHandlerStatusCommandFailed
            }
        }

        remoteCommandCenter.pauseCommand.setEnabled(true)
        remoteCommandCenter.pauseCommand.addTargetWithHandler { event: MPRemoteCommandEvent? ->
            debugLog("pauseCommand")
            if (playback.isPlaying.value) {
                launch { playback.pause() }
                MPRemoteCommandHandlerStatusSuccess
            } else {
                MPRemoteCommandHandlerStatusCommandFailed
            }
        }

        remoteCommandCenter.togglePlayPauseCommand.setEnabled(true)
        remoteCommandCenter.togglePlayPauseCommand.addTargetWithHandler { event: MPRemoteCommandEvent? ->
            launch {
                if (playback.isPlaying.value) playback.pause() else playback.play()
            }
            debugLog("togglePlayPauseCommand")
            MPRemoteCommandHandlerStatusSuccess
        }

//        remoteCommandCenter.skipForwardCommand.setEnabled(true)
//        remoteCommandCenter.skipForwardCommand.preferredIntervals = NSArray.arrayWithObject(NSNumber(double = 15.0))
//        remoteCommandCenter.skipForwardCommand.addTargetWithHandler { event: MPRemoteCommandEvent? ->
//            val seconds = (event as? MPSkipIntervalCommandEvent)?.interval ?: 15.0
//            val current = playback.currentPosition().toDouble().div(1000)
//            playback.seekTo(((current + seconds) * 1000).toLong())
//            debugLog("skipForwardCommand: $seconds")
//            MPRemoteCommandHandlerStatusSuccess
//        }
//
//        remoteCommandCenter.skipBackwardCommand.setEnabled(true)
//        remoteCommandCenter.skipBackwardCommand.preferredIntervals = NSArray.arrayWithObject(NSNumber(double = 15.0))
//        remoteCommandCenter.skipBackwardCommand.addTargetWithHandler { event: MPRemoteCommandEvent? ->
//            val seconds = (event as? MPSkipIntervalCommandEvent)?.interval ?: 15.0
//            val current = playback.currentPosition().toDouble().div(1000)
//            playback.seekTo(((current - seconds).coerceAtLeast(0.0) * 1000).toLong())
//            debugLog("skipBackwardCommand: $seconds")
//            MPRemoteCommandHandlerStatusSuccess
//        }

        remoteCommandCenter.nextTrackCommand.setEnabled(true)
        remoteCommandCenter.nextTrackCommand.addTargetWithHandler { event: MPRemoteCommandEvent? ->
            debugLog("nextTrackCommand")
            launch { playback.skipToNext() }
            MPRemoteCommandHandlerStatusSuccess
        }

        remoteCommandCenter.previousTrackCommand.setEnabled(true)
        remoteCommandCenter.previousTrackCommand.addTargetWithHandler { event: MPRemoteCommandEvent? ->
            debugLog("previousTrackCommand")
            launch { playback.skipToPrevious() }
            MPRemoteCommandHandlerStatusSuccess
        }

        remoteCommandCenter.changePlaybackPositionCommand.setEnabled(true)
        remoteCommandCenter.changePlaybackPositionCommand.addTargetWithHandler { event: MPRemoteCommandEvent? ->
            val seconds = (event as? MPChangePlaybackPositionCommandEvent)?.positionTime ?: 0.0
            val millisecond = seconds.times(1000L)

            debugLog("changePlaybackPositionCommand: $millisecond")
            launch { playback.seekTo(millisecond.toLong()) }
            MPRemoteCommandHandlerStatusSuccess
        }
    }
}
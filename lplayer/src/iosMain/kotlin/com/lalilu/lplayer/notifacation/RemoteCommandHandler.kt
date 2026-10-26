package com.lalilu.lplayer.notifacation

import com.lalilu.lplayer.playback.Playback
import com.lalilu.lplayer.action.launchPlayerAction
import kotlinx.coroutines.*
import platform.MediaPlayer.*

/** Native commands forward intent even while the decoder is buffering or preparing. */
object RemoteCommandHandler {
    private val center = MPRemoteCommandCenter.sharedCommandCenter()
    private var scope: CoroutineScope? = null
    private val targets = mutableListOf<Pair<MPRemoteCommand, Any>>()

    fun unbind() {
        scope?.cancel(); scope = null
        targets.forEach { (command, target) -> command.removeTarget(target) }
        targets.clear()
    }

    fun bindPlayback(playback: Playback) {
        unbind()
        val boundScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = boundScope
        fun bind(command: MPRemoteCommand, action: suspend (MPRemoteCommandEvent?) -> Unit) {
            command.enabled = true
            val target = command.addTargetWithHandler { event ->
                if (scope !== boundScope) MPRemoteCommandHandlerStatusCommandFailed
                else {
                    boundScope.launchPlayerAction { action(event) }
                    MPRemoteCommandHandlerStatusSuccess
                }
            }
            targets += command to target
        }
        bind(center.playCommand) { playback.play() }
        bind(center.pauseCommand) { playback.pause() }
        bind(center.stopCommand) { playback.stop() }
        bind(center.togglePlayPauseCommand) { playback.togglePlayPause() }
        bind(center.nextTrackCommand) { playback.skipToNext() }
        bind(center.previousTrackCommand) { playback.skipToPrevious() }
        bind(center.changePlaybackPositionCommand) { event ->
            val seconds = (event as? MPChangePlaybackPositionCommandEvent)?.positionTime
            if (seconds != null && seconds.isFinite()) playback.seekTo((seconds.coerceAtLeast(0.0) * 1000).toLong())
        }
    }
}

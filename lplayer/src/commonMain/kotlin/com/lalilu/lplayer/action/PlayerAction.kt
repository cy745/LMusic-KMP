package com.lalilu.lplayer.action

import com.lalilu.common.ext.io
import com.lalilu.lplayer.LPlayer
import com.lalilu.lplayer.extensions.PlayMode
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

sealed class PlayerAction() : Action {
    override fun action() {
        handlePlatformPlayerAction(this)
    }

    data object Play : PlayerAction()
    data object Pause : PlayerAction()
    data object SkipToNext : PlayerAction()
    data object SkipToPrevious : PlayerAction()
    data class SkipToIndex(val index: Int) : PlayerAction()
    data class PlayById(val id: String) : PlayerAction()
    data class SeekTo(val positionMs: Long) : PlayerAction()
    data class SetPlayMode(val playMode: PlayMode) : PlayerAction()
    data class PauseWhenCompletion(val cancel: Boolean = false) : PlayerAction()
}

expect fun handlePlatformPlayerAction(action: PlayerAction)

@OptIn(DelicateCoroutinesApi::class)
fun defaultPlayerActionHandler(action: PlayerAction) {
    GlobalScope.launch(Dispatchers.io) {
        when (action) {
            PlayerAction.Play -> LPlayer.instance.play()
            PlayerAction.Pause -> LPlayer.instance.pause()
            PlayerAction.SkipToNext -> LPlayer.instance.skipToNext()
            PlayerAction.SkipToPrevious -> LPlayer.instance.skipToPrevious()
            is PlayerAction.SkipToIndex -> LPlayer.instance.skipTo(action.index)
            is PlayerAction.SeekTo -> LPlayer.instance.seekTo(action.positionMs)
            is PlayerAction.PlayById -> {
                val index = LPlayer.instance.playlist.value
                    .indexOfFirst { item -> item.id == action.id }

                LPlayer.instance.skipTo(index)
            }

            else -> {

            }
        }
    }
}
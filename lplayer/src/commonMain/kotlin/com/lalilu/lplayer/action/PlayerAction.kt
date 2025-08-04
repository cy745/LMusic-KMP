package com.lalilu.lplayer.action

import com.lalilu.lplayer.extensions.PlayMode

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
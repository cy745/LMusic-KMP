package com.lalilu.lplayer.action

import com.lalilu.lplayer.LPlayer
import com.lalilu.lplayer.playback.AbstractPlayback

actual fun handlePlatformPlayerAction(action: PlayerAction) {
    when (action) {
        is PlayerAction.PauseWhenCompletion -> {
            (LPlayer.instance as? AbstractPlayback)?.setPauseWhenCompletion(action.cancel)
        }
        else -> defaultPlayerActionHandler(action)
    }
}

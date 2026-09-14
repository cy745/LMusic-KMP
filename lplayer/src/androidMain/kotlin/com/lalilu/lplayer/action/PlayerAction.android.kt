package com.lalilu.lplayer.action

actual fun handlePlatformPlayerAction(action: PlayerAction) {
    when (action) {
        // MService observes this preference, including changes made by system controllers.
        is PlayerAction.SetPlayMode -> com.lalilu.lplayer.LPlayerKV.playMode.value = action.playMode.name
        else -> defaultPlayerActionHandler(action)
    }
}

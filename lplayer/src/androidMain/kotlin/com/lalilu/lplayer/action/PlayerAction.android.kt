package com.lalilu.lplayer.action

actual fun handlePlatformPlayerAction(action: PlayerAction) {
    when (action) {
        else -> defaultPlayerActionHandler(action)
    }
}

package com.lalilu.lplayer.action

actual fun handlePlatformPlayerAction(action: PlayerAction) {
    // The service persists the confirmed mode; preference writes are not a second command channel.
    defaultPlayerActionHandler(action, persistModePreference = false)
}

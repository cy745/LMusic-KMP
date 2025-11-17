package com.lalilu.lplayer.action

import kotlinx.coroutines.DelicateCoroutinesApi

@OptIn(DelicateCoroutinesApi::class)
actual fun handlePlatformPlayerAction(action: PlayerAction) {
    defaultPlayerActionHandler(action)
}
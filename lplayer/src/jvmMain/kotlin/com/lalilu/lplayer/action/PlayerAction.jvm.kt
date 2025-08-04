package com.lalilu.lplayer.action

actual fun handlePlatformPlayerAction(action: PlayerAction) {
    when (action) {
        PlayerAction.Play -> TODO()
        PlayerAction.Pause -> TODO()
        PlayerAction.SkipToNext -> TODO()
        PlayerAction.SkipToPrevious -> TODO()
        is PlayerAction.SkipToIndex -> TODO()
        is PlayerAction.SeekTo -> TODO()
        is PlayerAction.PlayById -> TODO()
        is PlayerAction.PauseWhenCompletion -> TODO()
        is PlayerAction.SetPlayMode -> TODO()
    }
}
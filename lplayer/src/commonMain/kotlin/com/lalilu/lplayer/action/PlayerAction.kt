package com.lalilu.lplayer.action

import com.lalilu.common.ext.io
import com.lalilu.lmedia.LMedia
import com.lalilu.lmedia.entity.LAudio
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
    data object PlayOrPause : PlayerAction()
    data object SkipToNext : PlayerAction()
    data object SkipToPrevious : PlayerAction()
    data class SkipToIndex(val index: Int) : PlayerAction()
    data class AddToNext(val id: String) : PlayerAction()
    data class PlayById(val id: String) : PlayerAction()
    data class SeekTo(val positionMs: Long) : PlayerAction()
    data class SetPlayMode(val playMode: PlayMode) : PlayerAction()
    data class PauseWhenCompletion(val cancel: Boolean = false) : PlayerAction()
    data class UpdateList(
        val ids: List<String>,
        val id: String? = null,
        val start: Boolean = false
    ) : PlayerAction()
}

expect fun handlePlatformPlayerAction(action: PlayerAction)

@OptIn(DelicateCoroutinesApi::class)
fun defaultPlayerActionHandler(action: PlayerAction) {
    GlobalScope.launch(Dispatchers.io) {
        when (action) {
            PlayerAction.Play -> LPlayer.instance.play()
            PlayerAction.Pause -> LPlayer.instance.pause()
            PlayerAction.PlayOrPause -> LPlayer.instance
                .apply { if (isPlaying.value) play() else pause() }

            PlayerAction.SkipToNext -> LPlayer.instance.skipToNext()
            PlayerAction.SkipToPrevious -> LPlayer.instance.skipToPrevious()
            is PlayerAction.SkipToIndex -> LPlayer.instance.skipTo(action.index)
            is PlayerAction.SeekTo -> LPlayer.instance.seekTo(action.positionMs)
            is PlayerAction.PlayById -> {
                val index = LPlayer.instance.playlist.value
                    .indexOfFirst { item -> item.id() == action.id }

                LPlayer.instance.skipTo(index)
            }

            is PlayerAction.UpdateList -> {
                LPlayer.instance.updatePlaylist(
                    playlist = LMedia.instance.mapBy<LAudio>(action.ids),
                    startIndex = action.id?.let { action.ids.indexOf(it) }?.coerceAtLeast(0) ?: 0,
                    start = action.start
                )
            }

            else -> {

            }
        }
    }
}
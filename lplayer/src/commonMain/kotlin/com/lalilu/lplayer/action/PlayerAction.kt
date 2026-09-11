package com.lalilu.lplayer.action

import com.lalilu.common.ext.io
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.lmedia.domain.model.mediaKey
import com.lalilu.lplayer.playback.PlaybackMode
import kotlinx.coroutines.flow.first
import com.lalilu.lplayer.LPlayer
import com.lalilu.lplayer.LPlayerKV
import com.lalilu.lplayer.extensions.PlayMode
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import org.koin.mp.KoinPlatform

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
    data class PlayByKey(val key: MediaKey) : PlayerAction()
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

private val playerActionScope = CoroutineScope(Dispatchers.io + SupervisorJob())

fun defaultPlayerActionHandler(action: PlayerAction) {
    playerActionScope.launchPlayerAction(
        onFailure = { Logger.e(it) { "Player action failed: $action" } },
    ) {
        when (action) {
            PlayerAction.Play -> LPlayer.instance.play()
            PlayerAction.Pause -> LPlayer.instance.pause()
            PlayerAction.PlayOrPause -> LPlayer.instance.togglePlayPause()

            PlayerAction.SkipToNext -> LPlayer.instance.skipToNext()
            PlayerAction.SkipToPrevious -> LPlayer.instance.skipToPrevious()
            is PlayerAction.SetPlayMode -> {
                LPlayerKV.playMode.value = action.playMode.name
                LPlayer.instance.setPlaybackMode(when (action.playMode) {
                    PlayMode.ListRecycle -> PlaybackMode.LOOP
                    PlayMode.RepeatOne -> PlaybackMode.SINGLE_LOOP
                    PlayMode.Shuffle -> PlaybackMode.SHUFFLE
                })
            }

            is PlayerAction.PauseWhenCompletion -> LPlayer.instance.setPauseWhenCompletion(action.cancel)
            is PlayerAction.SkipToIndex -> LPlayer.instance.skipTo(action.index, true)
            is PlayerAction.SeekTo -> LPlayer.instance.seekTo(action.positionMs)
            is PlayerAction.PlayById -> {
                val list = LPlayer.instance.queue.stateSnapshot().list
                val keys = list.filter { it.id == action.id }.map { it.mediaKey }.distinct()
                if (keys.size == 1) {
                    LPlayer.instance.playAudio(list.first { it.mediaKey == keys.single() })
                }
            }

            is PlayerAction.PlayByKey -> {
                LPlayer.instance.queue.stateSnapshot().list
                    .firstOrNull { it.mediaKey == action.key }
                    ?.let { LPlayer.instance.playAudio(it) }
            }

            is PlayerAction.AddToNext -> {
                val audioRepo: AudioRepository = KoinPlatform.getKoin().get()
                audioRepo.getAudio(action.id).first()?.let { audio ->
                    LPlayer.instance.editQueue { addToNext(listOf(audio)) }
                }
            }

            is PlayerAction.UpdateList -> {
                val audioRepo: AudioRepository = KoinPlatform.getKoin().get()
                val audios = audioRepo.getAudios(action.ids).first()
                LPlayer.instance.updatePlaylist(
                    playlist = audios,
                    startIndex = action.id?.let { id -> audios.indexOfFirst { it.id == id } }
                        ?.coerceAtLeast(0) ?: 0,
                    start = action.start
                )
            }

        }
    }
}

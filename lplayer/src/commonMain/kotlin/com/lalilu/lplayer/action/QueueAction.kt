package com.lalilu.lplayer.action

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.common.ext.io
import com.lalilu.lplayer.LPlayer
import com.lalilu.lplayer.playback.Playback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import co.touchlab.kermit.Logger

private val queueActionScope = CoroutineScope(Dispatchers.io + SupervisorJob())

sealed class QueueAction : Action {

    override fun action() {
        queueActionScope.launchPlayerAction(
            onFailure = { Logger.e(it) { "Queue action failed: $this@QueueAction" } },
        ) {
            execute(LPlayer.instance)
        }
    }

    suspend fun execute(player: Playback) {
        val action = this
        // Snapshot-dependent operations are evaluated inside the queue's atomic update.
        player.editQueue {
            when (action) {
                is AddToPrevious -> insert(currentIndex, listOf(action.item))
                is AddToNext -> addToNext(listOf(action.item))
                is AddToStart -> addToStart(listOf(action.item))
                is AddToEnd -> addToEnd(listOf(action.item))
                is Remove -> remove(action.item)
                is RemoveById -> removeLegacyId(action.id)
                is RemoveByIndex -> removeAt(action.index)
                is Replace -> replace(action.index, action.item)
                is Move -> move(action.from, action.to)
                Clear -> clear()
            }
        }
    }

    data class AddToPrevious(val item: LAudio) : QueueAction()
    data class AddToNext(val item: LAudio) : QueueAction()
    data class AddToStart(val item: LAudio) : QueueAction()
    data class AddToEnd(val item: LAudio) : QueueAction()
    data class Remove(val item: LAudio) : QueueAction()
    data class RemoveById(val id: String) : QueueAction()
    data class RemoveByIndex(val index: Int) : QueueAction()
    data class Replace(val index: Int, val item: LAudio) : QueueAction()
    data class Move(val from: Int, val to: Int) : QueueAction()
    data object Clear : QueueAction()
}

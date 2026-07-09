package com.lalilu.lplayer.action

import com.lalilu.lmedia.domain.model.LAudio

sealed class QueueAction : Action {

    override fun action() {
        handlePlatformQueueAction(this)
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

expect fun handlePlatformQueueAction(action: QueueAction)

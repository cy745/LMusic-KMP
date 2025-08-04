package com.lalilu.lplayer.action

import com.lalilu.lmedia.entity.LItem

sealed class QueueAction : Action {

    override fun action() {
        handlePlatformQueueAction(this)
    }

    data class AddToPrevious(val item: LItem) : QueueAction()
    data class AddToNext(val item: LItem) : QueueAction()
    data class AddToStart(val item: LItem) : QueueAction()
    data class AddToEnd(val item: LItem) : QueueAction()
    data class Remove(val item: LItem) : QueueAction()
    data class RemoveById(val id: String) : QueueAction()
    data class RemoveByIndex(val index: Int) : QueueAction()
    data class Replace(val index: Int, val item: LItem) : QueueAction()
    data class Move(val from: Int, val to: Int) : QueueAction()
    data object Clear : QueueAction()
}

expect fun handlePlatformQueueAction(action: QueueAction)

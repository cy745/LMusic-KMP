package com.lalilu.lplayer.action

actual fun handlePlatformQueueAction(action: QueueAction) {
    when (action) {
        is QueueAction.AddToEnd -> TODO()
        is QueueAction.AddToNext -> TODO()
        is QueueAction.AddToPrevious -> TODO()
        is QueueAction.AddToStart -> TODO()
        is QueueAction.Move -> TODO()
        is QueueAction.Remove -> TODO()
        is QueueAction.RemoveById -> TODO()
        is QueueAction.RemoveByIndex -> TODO()
        is QueueAction.Replace -> TODO()
        QueueAction.Clear -> TODO()
    }
}
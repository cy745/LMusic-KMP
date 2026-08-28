package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


@OptIn(ExperimentalCoroutinesApi::class)
class PlayableQueueImpl : PlayableQueue {
    private val _rawQueue = MutableStateFlow(QueueState())
    private val updateMutex = Mutex()

    // _rawQueue 本身已经是热 StateFlow；直接暴露只读视图，确保首次订阅前的更新也能被 stateSnapshot 读取。
    override val expandedItems: StateFlow<QueueState> = _rawQueue.asStateFlow()

    override suspend fun update(
        updateReason: QueueUpdateReason,
        predicate: (QueueState) -> Boolean,
        block: QueueUpdateRequest.() -> Unit,
    ) = updateMutex.withLock {
        val current = _rawQueue.value
        if (!predicate(current)) return@withLock

        val request = QueueUpdateRequest(current).apply(block)
        _rawQueue.value = request.build(updateReason)
    }

    override fun previousOf(target: LAudio): LAudio? {
        return _rawQueue.value.list.run {
            val index = indexOfFirst { it.id == target.id }
            getOrNull(index - 1)
        }
    }

    override fun nextOf(target: LAudio): LAudio? {
        return _rawQueue.value.list.run {
            val index = indexOfFirst { it.id == target.id }
            getOrNull(index + 1)
        }
    }
}

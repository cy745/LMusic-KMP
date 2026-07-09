package com.lalilu.lplayer.playback

import com.lalilu.common.ext.io
import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn


@OptIn(ExperimentalCoroutinesApi::class)
class PlayableQueueImpl(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.io + SupervisorJob())
) : PlayableQueue {
    private val _rawQueue = MutableStateFlow(QueueState())

    override val expandedItems: StateFlow<QueueState> = _rawQueue
        .stateIn(scope = scope, started = SharingStarted.Lazily, initialValue = QueueState())

    override suspend fun update(
        updateReason: QueueUpdateReason,
        block: QueueUpdateRequest.() -> Unit
    ) {
        val request = QueueUpdateRequest(_rawQueue.value)
        request.block()
        _rawQueue.emit(request.build(updateReason))
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

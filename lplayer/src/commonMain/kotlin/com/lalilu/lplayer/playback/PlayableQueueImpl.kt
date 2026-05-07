package com.lalilu.lplayer.playback

import com.lalilu.common.ext.io
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*


@OptIn(ExperimentalCoroutinesApi::class)
class PlayableQueueImpl(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.io + SupervisorJob())
) : PlayableQueue {
    private val _rawQueue = MutableStateFlow(listOf<LAudio>() to 0)

    override val expandedItems: StateFlow<QueueState> = _rawQueue
        .mapLatest { (list, index) -> QueueState(list, index) }
        .stateIn(scope = scope, started = SharingStarted.Lazily, initialValue = QueueState())

    override fun addToStart(item: LItem) {
        _rawQueue.update {
            val list = item.toPlayable() + it.first
            val index = it.second + 1 // 当前播放元素被后移一位
            list to index
        }
    }

    override fun addToEnd(item: LItem) {
        _rawQueue.update {
            val list = it.first + item.toPlayable()
            val index = it.second  // 当前播放元素位置不变
            list to index
        }
    }

    override fun addToNext(item: LItem) {
        _rawQueue.update {
            val list = it.first.toMutableList()
            val targetIndex = (it.second + 1).coerceIn(0, list.size)
            list.addAll(targetIndex, item.toPlayable())
            list to it.second
        }
    }

    override fun switchTo(index: Int) {
        // 确保索引在队列范围内
        _rawQueue.update { pair -> if (index in pair.first.indices) pair.first to index else pair }
    }

    override fun replaceAll(items: List<LAudio>, index: Int) {
        _rawQueue.update { pair ->
            var targetIndex = index

            // 重新计算当前播放元素位置
            if (targetIndex == -1) {
                // 获取当前播放元素的Key
                val currentKey = pair.first.getOrNull(pair.second)?.idValue()
                targetIndex = items.indexOfFirst { it.idValue() == currentKey }
            }

            items to targetIndex.coerceAtMost(items.lastIndex)
        }
    }

    override fun remove(item: LAudio) {
        _rawQueue.update { (list, index) -> list.filter { it.idValue() != item.idValue() } to index }
    }

    override fun clear() {
        _rawQueue.update { emptyList<LAudio>() to 0 }
    }

    override fun previousOf(target: LAudio): LAudio? {
        return _rawQueue.value.first.run {
            val index = indexOfFirst { it.idValue() == target.idValue() }
            getOrNull(index - 1)
        }
    }

    override fun nextOf(target: LAudio): LAudio? {
        return _rawQueue.value.first.run {
            val index = indexOfFirst { it.idValue() == target.idValue() }
            getOrNull(index + 1)
        }
    }
}


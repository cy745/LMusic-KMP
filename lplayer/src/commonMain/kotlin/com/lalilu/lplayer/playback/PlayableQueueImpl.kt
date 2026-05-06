package com.lalilu.lplayer.playback

import com.lalilu.common.ext.io
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import com.lalilu.lmedia.entity.ref
import com.lalilu.lplayer.extensions.add
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*


@OptIn(ExperimentalCoroutinesApi::class)
class PlayableQueueImpl(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.io + SupervisorJob())
) : PlayableQueue {
    private val _rawQueue = MutableStateFlow(listOf<Playable<LAudio>>() to 0)

    override val expandedItems: StateFlow<QueueState> = _rawQueue
        .mapLatest { (list, index) -> QueueState(list.flatten(), index) }
        .stateIn(scope = scope, started = SharingStarted.Lazily, initialValue = QueueState())

    override fun addToStart(item: LItem) {
        _rawQueue.update {
            val list = listOf(item.toPlayable()) + it.first
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
            val targetIndex = (it.second + 1).coerceIn(it.first.indices)
            list.add(targetIndex, item)
            list to it.second
        }
    }

    override fun switchTo(index: Int) {
        // 确保索引在队列范围内
        _rawQueue.update { pair -> if (index in pair.first.indices) pair.first to index else pair }
    }

    override fun replaceAll(items: List<LItem>, index: Int) {
        _rawQueue.update { pair ->
            val newList = items.map { item -> item.toPlayable() }
            var targetIndex = index

            // 重新计算当前播放元素位置
            if (targetIndex == -1) {
                // 获取当前播放元素的Key
                val currentKey = pair.first.getOrNull(pair.second)?.key
                targetIndex = newList.indexOfFirst { it.key == currentKey }
            }

            newList to targetIndex.coerceAtMost(newList.lastIndex)
        }
    }

    override fun update(
        target: Playable<LAudio>,
        item: LItem
    ) {
        // 找到目标项，则替换为新的项，否则保持不变
        _rawQueue.update { (list, index) ->
            list.map { playable -> if (playable.key == target.key) item.toPlayable() else playable } to index
        }
    }

    override fun find(item: LItem): List<Playable<LAudio>> {
        return when (item) {
            is LAudio -> expandedItems.value.list.filter { it.item.idValue() == item.idValue() }
            else -> _rawQueue.value.first.filter { playable ->
                when (playable) {
                    is Playable.Item -> false
                    is Playable.Items<LAudio, *> -> playable.source.idValue() == item.idValue()
                }
            }
        }
    }

    override fun remove(playable: Playable<LAudio>) {
        _rawQueue.update { (list, index) -> list.filter { it.key != playable.key } to index }
    }

    override fun clear() {
        _rawQueue.update { emptyList<Playable<LAudio>>() to 0 }
    }

    override fun previousOf(target: Playable<LAudio>): Playable<LAudio>? {
        return when (target) {
            is Playable.Item<LAudio> -> expandedItems.value.list.run {
                val index = indexOfFirst { it.key == target.key }
                getOrNull(index - 1)
            }

            is Playable.Items<LAudio, *> -> _rawQueue.value.first.run {
                val index = indexOfFirst { it.key == target.key }
                getOrNull(index - 1)
            }
        }
    }

    override fun nextOf(target: Playable<LAudio>): Playable<LAudio>? {
        return when (target) {
            is Playable.Item<LAudio> -> expandedItems.value.list.run {
                val index = indexOfFirst { it.key == target.key }
                getOrNull(index + 1)
            }

            is Playable.Items<LAudio, *> -> _rawQueue.value.first.run {
                val index = indexOfFirst { it.key == target.key }
                getOrNull(index + 1)
            }
        }
    }

    private fun LItem.toPlayable(): Playable<LAudio> {
        return when (this) {
            is LAudio -> Playable.Item(this)
            else -> Playable.Items(items = ref<LAudio>(), source = this)
        }
    }
}


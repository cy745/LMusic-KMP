package com.lalilu.lplayer.playback

import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import com.lalilu.lmedia.entity.ref
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest

data class QueueState(
    val list: List<LAudio> = emptyList(),
    val index: Int = 0,
    val updateReason: QueueUpdateReason = QueueUpdateReason.Unknown
) {
    /** 重新排列播放列表，将当前索引处的元素及其后的元素移到列表前面。 */
    fun rearrange(): List<LAudio> {
        if (index !in list.indices) return list
        return (list.drop(index) + list.take(index))
            .distinctBy { it.idValue() }
    }

    /** 获取当前播放项 */
    fun currentItem(): LAudio? = list.getOrNull(index)
}

@OptIn(ExperimentalCoroutinesApi::class)
interface PlayableQueue {
    val expandedItems: StateFlow<QueueState>

    fun stateSnapshot(): QueueState = expandedItems.value
    fun currentItem(): LAudio? = stateSnapshot().currentItem()
    fun currentItemFlow(): Flow<LAudio?> = expandedItems.mapLatest { it.currentItem() }

    /**
     * 更新播放队列。
     * 所有操作在一次原子更新中完成，只触发一次 StateFlow emit。
     *
     * @param updateReason 队列更新原因，默认 Inner
     * @param block 在 [QueueUpdateRequest] 作用域内执行的操作序列
     */
    suspend fun update(
        updateReason: QueueUpdateReason = QueueUpdateReason.Inner,
        block: QueueUpdateRequest.() -> Unit
    )

    fun nextOf(target: LAudio): LAudio?
    fun previousOf(target: LAudio): LAudio?
}

/** 将 [LItem] 转换为可播放的 [LAudio] 列表。 */
internal fun LItem.toPlayable(): List<LAudio> {
    return when (this) {
        is LAudio -> listOf(this)
        else -> ref<LAudio>()
    }
}

package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.mediaKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest

data class QueueState(
    val list: List<LAudio> = emptyList(),
    val index: Int = 0,
    val updateReason: QueueUpdateReason = QueueUpdateReason.Unknown,
    /** Explicit selection/replacement can reload the same song without changing its slot. */
    val selectionRevision: Long = 0L,
    /** Changes made through the queue command boundary; native mirrors preserve this number. */
    val editRevision: Long = 0L,
    /**
     * 当前项是否为「用户显式选中并播放」（点击列表行、播放某首歌），
     * 而不是队列自己走了一步（上一首/下一首、自动播放下一首）。
     *
     * 判据来自队列命令边界（`selectOrInsert` 是「播放某一项」的唯一入口），不是 UI 猜的：
     * 队列左旋 `n-1` 位时，「点最后一行」与「上一首」得到的新旧列表完全一样，
     * 只有成因能区分这两者。
     *
     * 与 [selectionRevision] 的区别：后者由 `switchTo` / `replaceAll` 打点，
     * 上一首/下一首同样会走 `switchTo`，所以它区分不了「步进」和「选中」。
     */
    val currentPickedByUser: Boolean = false,
) {
    /** 重新排列播放列表，将当前索引处的元素及其后的元素移到列表前面。 */
    fun rearrange(): List<LAudio> {
        if (index !in list.indices) return list
        return (list.drop(index) + list.take(index))
            .distinctBy { it.mediaKey }
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
     * @param predicate 在同一原子区间内检查当前队列；返回 false 时不执行也不发送更新
     * @param block 在 [QueueUpdateRequest] 作用域内执行的操作序列
     */
    suspend fun update(
        updateReason: QueueUpdateReason = QueueUpdateReason.Inner,
        predicate: (QueueState) -> Boolean = { true },
        block: QueueUpdateRequest.() -> Unit
    )

    fun nextOf(target: LAudio): LAudio?
    fun previousOf(target: LAudio): LAudio?
}

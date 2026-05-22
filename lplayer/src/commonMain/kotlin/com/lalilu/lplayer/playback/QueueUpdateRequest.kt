package com.lalilu.lplayer.playback

import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem

/**
 * 播放队列更新请求，用于构建一次原子性的队列更新。
 *
 * 构造时固定当前 [QueueState] 的快照，后续所有操作均在此快照基础上累积，
 * 内部维护 [pendingList] 和 [pendingIndex] 两个可变状态。
 * 最终通过 [build] 产出新的 [QueueState]。
 *
 * 支持链式调用：
 * ```
 * val req = QueueUpdateRequest(snapshot)
 *     .addToStart(item1)
 *     .addToNext(item2)
 *     .switchTo(0)
 * val newState = req.build(QueueUpdateReason.Inner)
 * ```
 *
 * 在 [PlayableQueue.update] 作用域内可省略手动构造：
 * ```
 * queue.update {
 *     addToStart(item1)
 *     addToNext(item2)
 * }
 * ```
 */
class QueueUpdateRequest(
    snapshot: QueueState
) : QueueMutationOps<QueueUpdateRequest> {
    private var pendingList: List<LAudio> = snapshot.list
    private var pendingIndex: Int = snapshot.index

    /**
     * 添加一个播放项到队列开头。
     * 当前播放索引会后移一位。
     */
    override fun addToStart(item: LItem): QueueUpdateRequest {
        pendingList = item.toPlayable() + pendingList
        pendingIndex += 1
        return this
    }

    /**
     * 添加一个播放项到队列末尾。
     * 当前播放索引不变。
     */
    override fun addToEnd(item: LItem): QueueUpdateRequest {
        pendingList = pendingList + item.toPlayable()
        return this
    }

    /**
     * 添加一个播放项到当前播放元素之后。
     * 当前播放索引不变。
     */
    override fun addToNext(item: LItem): QueueUpdateRequest {
        val targetIndex = (pendingIndex + 1).coerceIn(0, pendingList.size)
        pendingList = pendingList.toMutableList().apply {
            addAll(targetIndex, item.toPlayable())
        }
        return this
    }

    /**
     * 切换当前播放项到指定索引。
     * 如果索引超出 pendingList 范围，则不执行任何操作。
     */
    override fun switchTo(index: Int): QueueUpdateRequest {
        if (index in pendingList.indices) {
            pendingIndex = index
        }
        return this
    }

    /**
     * 替换所有播放项。
     *
     * @param items 新的播放项列表
     * @param index 新的当前播放项索引。当值为 -1 时，
     *              自动在 items 中查找与当前播放项 id 匹配的元素位置作为新索引。
     */
    override fun replaceAll(items: List<LAudio>, index: Int): QueueUpdateRequest {
        var targetIndex = index
        if (targetIndex == -1) {
            val currentKey = pendingList.getOrNull(pendingIndex)?.idValue()
            targetIndex = items.indexOfFirst { it.idValue() == currentKey }
        }
        pendingList = items
        pendingIndex = targetIndex.coerceAtMost(items.lastIndex)
        return this
    }

    /** 替换所有播放项，并自动计算当前播放项的位置。 */
    fun replaceAll(items: List<LAudio>) = replaceAll(items, -1)

    /**
     * 移除指定播放项。
     * 按 idValue 匹配移除，只移除第一个匹配项。
     */
    override fun remove(item: LAudio): QueueUpdateRequest {
        pendingList = pendingList.filter { it.idValue() != item.idValue() }
        return this
    }

    /** 清空所有播放项，索引重置为 0。 */
    override fun clear(): QueueUpdateRequest {
        pendingList = emptyList()
        pendingIndex = 0
        return this
    }

    /**
     * 构建最终的 [QueueState]。
     *
     * @param updateReason 队列更新原因，会透传到 [QueueState.updateReason]
     */
    fun build(updateReason: QueueUpdateReason): QueueState = QueueState(
        list = pendingList,
        index = pendingIndex,
        updateReason = updateReason
    )
}

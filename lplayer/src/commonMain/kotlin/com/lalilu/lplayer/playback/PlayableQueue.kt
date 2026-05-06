package com.lalilu.lplayer.playback

import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest

data class QueueState(
    val list: List<Playable.Item<LAudio>> = emptyList(),
    val index: Int = 0,
) {
    /**
     * 重新排列播放列表，将当前索引处的元素及其后的元素移到列表前面。
     * 如果当前索引无效，则返回原列表。
     *
     * @return 重新排列后的播放项列表
     */
    fun rearrange(): List<Playable.Item<LAudio>> {
        if (index !in list.indices) return list
        return list.drop(index) + list.take(index)
    }

    /**
     * 获取当前播放项
     */
    fun currentItem(): Playable.Item<LAudio>? {
        return list.getOrNull(index)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
interface PlayableQueue {
    val expandedItems: StateFlow<QueueState>

    /**
     * 获取当前播放项状态快照
     */
    fun stateSnapshot(): QueueState = expandedItems.value

    /**
     * 获取当前播放项
     */
    fun currentItem(): Playable.Item<LAudio>? = stateSnapshot().currentItem()

    /**
     * 获取当前播放项流
     */
    fun currentItemFlow(): Flow<Playable.Item<LAudio>?> = expandedItems.mapLatest { it.currentItem() }

    /**
     * 添加一个播放项到开头
     */
    fun addToStart(item: LItem)

    /**
     * 添加一个播放项到末尾
     */
    fun addToEnd(item: LItem)

    /**
     * 添加到当前播放元素后
     */
    fun addToNext(item: LItem)

    /**
     * 切换播放项
     *
     * @param index 新的当前播放元素索引
     */
    fun switchTo(index: Int)

    /**
     * 替换所有播放项
     *
     * @param items 新的播放项列表
     * @param index 新的当前播放元素的索引，为-1时需要重新计算当前播放元素索引
     */
    fun replaceAll(items: List<LItem>, index: Int = -1)

    /**
     * 更新一个播放项
     *
     * @param target 目标参考播放项
     * @param item 新的播放项
     */
    fun update(target: Playable<LAudio>, item: LItem)

    /**
     * 查找所有相似的播放项
     *
     * @return 找到的播放项列表
     */
    fun find(item: LItem): List<Playable<LAudio>>

    /**
     * 移除一个播放项
     */
    fun remove(playable: Playable<LAudio>)

    /**
     * 清空播放项列表
     */
    fun clear()

    /**
     * 获取[target]的下一个播放项
     *
     * @param target 目标参考播放项
     */
    fun nextOf(target: Playable<LAudio>): Playable<LAudio>?

    /**
     * 获取[target]的上一个播放项
     *
     * @param target 目标参考播放项
     */
    fun previousOf(target: Playable<LAudio>): Playable<LAudio>?
}

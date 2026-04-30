package com.lalilu.lplayer.playback

import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import kotlinx.coroutines.flow.StateFlow

data class QueueState(
    val list: List<Playable.Item<LAudio>> = emptyList(),
    val index: Int = 0,
)

interface PlayableQueue {
    val expandedItems: StateFlow<QueueState>

    /**
     * 添加一个播放项到开头
     */
    fun addToStart(item: LItem)

    /**
     * 添加一个播放项到末尾
     */
    fun addToEnd(item: LItem)

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

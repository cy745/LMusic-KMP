package com.lalilu.lplayer.components

import com.lalilu.extensions.Item
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.mediaKey

/**
 * 队列变化后，播放列表该采取哪种滚动策略。
 */
internal enum class PlaylistScrollPlan {
    /** 新旧队首都不在屏幕上：直接换列表，保持当前滚动位置 */
    KeepScroll,

    /** 新队首在屏幕上、旧队首不在：先清空再恢复以稳定触发元素动画，然后回到顶部 */
    RebuildAtTop,

    /** 其余：直接换列表并回到顶部 */
    JumpToTop,
}

/**
 * 根据「谁在屏幕上」和「这次变化是不是用户点了列表里的某一行」决定滚动策略。
 *
 * 为什么需要 [pickedByUser]：队列左旋 `n-1` 位时（按上一首 / 点击列表最后一行），两者的
 * 新旧列表完全一样，被判断的那个元素（新队首 = 旧列表最后一个元素）也都停在屏幕上，
 * 所以只看可见状态必然给出同一个答案。成因由队列命令边界提供，不在这里猜。
 *
 * - 用户点了某一行：被点中的那首必须被带进视野（列表回到顶部）；
 * - 上一首/下一首：列表停在原处，不去打扰正在浏览的位置。
 *
 * @param oldList 变化前的列表（`Item.key` 即当前屏幕上正在使用的 key）
 * @param newHead 变化后的首元素
 * @param visibleKeys 当前屏幕上可见项的 key（`LazyListItemInfo.key` 本身是 `Any`）
 * @param pickedByUser 本次变化是否由「用户点了列表里的某一行」触发
 */
internal fun planPlaylistScroll(
    oldList: List<Item<LAudio>>,
    newHead: Item<LAudio>,
    visibleKeys: Set<Any>,
    pickedByUser: Boolean = false,
): PlaylistScrollPlan {
    val oldHeadKey = oldList.firstOrNull()?.key
    val isNewHeadVisible = newHead.key in visibleKeys
    val isOldHeadVisible = oldHeadKey != null && oldHeadKey in visibleKeys

    return when {
        // 用户点了列表里的某一行：无论它的 key 是否换过，都要把被点中的那首带回视野
        pickedByUser && !isOldHeadVisible -> PlaylistScrollPlan.RebuildAtTop

        // 新旧队首都不可见：原地换列表即可（按上一首停在原处就靠这一条）
        !isNewHeadVisible && !isOldHeadVisible -> PlaylistScrollPlan.KeepScroll

        // 新队首在屏、旧队首不在：清空重建，让新列表干净地从顶部出现
        isNewHeadVisible && !isOldHeadVisible -> PlaylistScrollPlan.RebuildAtTop

        else -> PlaylistScrollPlan.JumpToTop
    }
}

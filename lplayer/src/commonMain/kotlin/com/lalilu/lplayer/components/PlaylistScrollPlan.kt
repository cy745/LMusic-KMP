package com.lalilu.lplayer.components

import com.lalilu.extensions.Item
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.mediaKey

/**
 * 队列变化后，播放列表该采取哪种滚动策略。
 */
internal enum class PlaylistScrollPlan {
    /** 当前歌曲不变，或新旧队首都不在屏幕上：直接换列表，保持当前滚动位置 */
    KeepScroll,

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
 * - 当前歌曲未变（设置下一首、排序、元数据更新）：保持浏览位置；
 * - 用户点了另一行播放：被点中的那首必须被带进视野（列表回到顶部）；
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
    // pickedByUser 描述当前歌曲的选择来源，会随排序/元数据更新保留，不能当作一次新的点击。
    // 当前歌曲没变时，让 LazyColumn 依靠保留的 key 维持可见锚点，不重建列表或滚回顶部。
    if (oldList.firstOrNull()?.data?.mediaKey == newHead.data.mediaKey) {
        return PlaylistScrollPlan.KeepScroll
    }

    val oldHeadKey = oldList.firstOrNull()?.key
    val isNewHeadVisible = newHead.key in visibleKeys
    val isOldHeadVisible = oldHeadKey != null && oldHeadKey in visibleKeys

    return when {
        // 用户点了列表里的某一行：无论它的 key 是否换过，都要把被点中的那首带回视野
        pickedByUser && !isOldHeadVisible -> PlaylistScrollPlan.JumpToTop

        // 新旧队首都不可见：原地换列表即可（按上一首停在原处就靠这一条）
        !isNewHeadVisible && !isOldHeadVisible -> PlaylistScrollPlan.KeepScroll

        // 新队首在屏、旧队首不在：回到顶部，但保留现有行及封面的组合状态
        isNewHeadVisible && !isOldHeadVisible -> PlaylistScrollPlan.JumpToTop

        else -> PlaylistScrollPlan.JumpToTop
    }
}

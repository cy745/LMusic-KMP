package com.lalilu.lplayer.components

import com.lalilu.extensions.Item
import com.lalilu.lmedia.domain.model.LAudio
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [planPlaylistScroll] 的行为契约。
 *
 * 背景：队列左旋 `n-1` 位时，「按上一首」与「点击列表最后一行」产生的新旧列表完全一样，
 * 被判断的元素（新队首 = 旧列表最后一个元素）也都停在屏幕上，所以可见状态无法区分两者。
 * 判据因此来自队列命令边界（[pickedByUser]）：点行要把那首带回视野，切歌要原地不动。
 *
 * 用例名即效果描述。
 */
class PlaylistScrollPlanTest {

    private val a = LAudio(id = "a", mediaSourceName = "local")
    private val b = LAudio(id = "b", mediaSourceName = "local")
    private val c = LAudio(id = "c", mediaSourceName = "local")

    /** 旧列表，key 用可辨识的固定前缀 */
    private fun oldList(vararg source: LAudio): List<Item<LAudio>> =
        source.map { Item(data = it, key = "old:${it.id}") }

    @Test
    fun pickingARowWhileScrolledRebuildsAtTop() {
        // 点了列表最后一行：它换过新 key（表现为顶部淡入一行），屏幕上已看不到旧队首
        val old = oldList(a, b, c)
        val newHead = Item(data = c, key = "fresh-c")

        val plan = planPlaylistScroll(old, newHead, visibleKeys = setOf("old:b"), pickedByUser = true)

        assertEquals(PlaylistScrollPlan.RebuildAtTop, plan)
    }

    @Test
    fun playingPreviousWhileScrolledKeepsScroll() {
        // 数据与上面完全相同，但成因是「上一首」：列表应当停在原处，不打扰正在浏览的位置
        val old = oldList(a, b, c)
        val newHead = Item(data = c, key = "fresh-c")

        val plan = planPlaylistScroll(old, newHead, visibleKeys = setOf("old:b"), pickedByUser = false)

        assertEquals(PlaylistScrollPlan.KeepScroll, plan)
    }

    @Test
    fun pickingARowWhileTheListIsAtTheTopJumpsToTop() {
        // 列表本来就在顶部，旧队首可见：不需要清空重建，保住当前的元素动画
        val old = oldList(a, b, c)
        val newHead = Item(data = c, key = "fresh-c")

        val plan = planPlaylistScroll(old, newHead, visibleKeys = setOf("old:a"), pickedByUser = true)

        assertEquals(PlaylistScrollPlan.JumpToTop, plan)
    }

    @Test
    fun playingNextWhileTheListIsAtTheTopJumpsToTop() {
        val old = oldList(a, b, c)
        val newHead = Item(data = b, key = "old:b")

        val plan = planPlaylistScroll(old, newHead, visibleKeys = setOf("old:a", "old:b"))

        assertEquals(PlaylistScrollPlan.JumpToTop, plan)
    }

    @Test
    fun steppingWhileScrolledAwayKeepsScroll() {
        // 自动下一首/下一首：新队首是保留下来的元素，但已滚出屏幕，列表不该被拉回顶部
        val old = oldList(a, b, c)
        val newHead = Item(data = b, key = "old:b")

        val plan = planPlaylistScroll(old, newHead, visibleKeys = setOf("old:c"), pickedByUser = false)

        assertEquals(PlaylistScrollPlan.KeepScroll, plan)
    }

    @Test
    fun visibleNewHeadWithOldHeadOffScreenRebuildsAtTop() {
        // 队列被换成别的内容，新队首在屏、旧队首不在
        val old = oldList(a, b, c)
        val newHead = Item(data = c, key = "old:c")

        val plan = planPlaylistScroll(old, newHead, visibleKeys = setOf("old:b", "old:c"))

        assertEquals(PlaylistScrollPlan.RebuildAtTop, plan)
    }

    @Test
    fun visibleOldHeadJumpsToTopEvenIfNewHeadIsOffScreen() {
        val old = oldList(a, b, c)
        val newHead = Item(data = c, key = "old:c")

        val plan = planPlaylistScroll(old, newHead, visibleKeys = setOf("old:a"))

        assertEquals(PlaylistScrollPlan.JumpToTop, plan)
    }

    @Test
    fun emptyOldListKeepsScroll() {
        // 初次加载：没有可比对的旧列表，列表本来就从顶部开始，不需要滚动
        val newHead = Item(data = a, key = "old:a")

        val plan = planPlaylistScroll(emptyList(), newHead, visibleKeys = emptySet())

        assertEquals(PlaylistScrollPlan.KeepScroll, plan)
    }
}

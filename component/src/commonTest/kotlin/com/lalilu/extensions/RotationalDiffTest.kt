package com.lalilu.extensions

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [rotationalDiff] 的行为契约。
 *
 * 场景约定：播放队列是「正在播放的元素排在队首」的循环列表，播放下一首 = 把队首搬到队尾，
 * 所以新列表 = 旧列表左旋 p 位，p 表示「往下走了几首」。
 *
 * 期望效果（已与需求确认）：
 * - 常规：始终「先让队首那一段离场，再让它们在列表末尾重新出现」——也就是旧的队首段换新 key，
 *   其余元素沿用旧 key 整体上移。这与旋转幅度无关；
 * - 例外：新队首原本就是旧列表的最后一个元素（`p == n-1`）时，只让新队首换新 key，
 *   表现为「顶部淡入一行、其余整体下移」；否则会退化成「几乎整表淡出再淡入」。
 *   这个位置天然有歧义（也可能是「点了列表最后一行」，两者数据上无法区分），
 *   这里统一按「上一首」处理；由此带来的滚动落点问题由视图层按元素身份判断可见性解决。
 *
 * 用例名即效果描述，review 用例名就等于 review 效果。
 */
class RotationalDiffTest {

    private data class Row(val id: String, val title: String = id)

    /** 21 个元素，便于覆盖「过半」这个关键的翻转点（p >= 11） */
    private val ids: List<String> = (0 until 21).map { "a$it" }

    private fun oldList(vararg source: String): List<Item<Row>> =
        source.map { Item(data = Row(it), key = "old:$it") }

    /** 与生产调用一致：同 id 视为同一个元素，标题变化不影响 key 复用 */
    private val sameId: (Row, Row) -> Boolean = { a, b -> a.id == b.id }
    private val sameContent: (Row, Row) -> Boolean = { a, b -> a == b }

    /** 左旋 p 位，模拟「往下播放了 p 首」 */
    private fun rotate(p: Int): List<Row> = (ids.drop(p) + ids.take(p)).map { Row(it) }

    private fun diffAt(
        p: Int,
        old: List<Item<Row>> = oldList(*ids.toTypedArray())
    ): List<Item<Row>> = old.rotationalDiff(
        rotate(p),
        getId = { it.id },
        isSameItem = sameId,
        isSameContent = sameContent
    )

    /** 换了新 key 的元素 id，即「离场后在末尾重新出现」的那一批 */
    private fun List<Item<Row>>.freshIds(): List<String> =
        filterNot { it.key.startsWith("old:") }.map { it.data.id }

    private fun List<Item<Row>>.ids(): List<String> = map { it.data.id }

    private fun List<Item<Row>>.keyOf(id: String): String = first { it.data.id == id }.key

    /** 断言「先删掉队首 p 段、再在末尾加回」这一效果 */
    private fun assertFreshIsHeadSegment(p: Int, result: List<Item<Row>>) {
        assertEquals(rotate(p).map { it.id }, result.ids(), "返回顺序必须与新列表一致")
        assertEquals(ids.take(p), result.freshIds(), "p=$p 时应是旧列表队首的 $p 个元素换新 key")
    }

    /** 回退路径的结构等价断言：key 里带随机 generation，所以只比较「谁新、谁复用了哪个旧 key」 */
    private fun assertSameAsLegacyDiff(old: List<Item<Row>>, new: List<Row>) {
        val expected = old.diff(new, getId = { it.id }, isSameItem = sameId, isSameContent = sameContent)
        val actual = old.rotationalDiff(new, getId = { it.id }, isSameItem = sameId, isSameContent = sameContent)

        assertEquals(expected.ids(), actual.ids(), "元素顺序应与原有 diff 一致")
        assertEquals(expected.freshIds(), actual.freshIds(), "换新 key 的元素应与原有 diff 一致")
        assertEquals(
            expected.filter { it.key.startsWith("old:") }.map { it.data.id to it.key },
            actual.filter { it.key.startsWith("old:") }.map { it.data.id to it.key },
            "复用旧 key 的部分应与原有 diff 完全一致",
        )
    }

    @Test
    fun identicalListKeepsEveryKey() {
        val result = diffAt(0)

        assertEquals(ids, result.ids())
        assertEquals(emptyList(), result.freshIds())
    }

    @Test
    fun playingNextMovesOnlyTheOldHeadToTheTail() {
        val result = diffAt(1)

        assertFreshIsHeadSegment(1, result)
        // 旧队首 a0 消失并在末尾出现，原本的第二首 a1 上移接任队首
        assertEquals(listOf("a0"), result.freshIds())
        assertEquals("old:a1", result.keyOf("a1"))
    }

    @Test
    fun smallForwardJumpMovesTheHeadSegmentToTheTail() {
        assertFreshIsHeadSegment(5, diffAt(5))
    }

    @Test
    fun halfwayJumpMovesTheHeadSegmentToTheTail() {
        // p=10 时旧 diff 恰好也保留旧尾块，两者结论一致
        assertFreshIsHeadSegment(10, diffAt(10))
    }

    @Test
    fun jumpPastHalfwayStillMovesTheHeadSegmentToTheTail() {
        // 核心：p=15 时旧 diff 会翻转成「保留旧前块、把旧尾块挪到队首」，
        // 表现为保留的元素整体被推到末尾。这里必须仍然是「队首段换新 key」。
        assertFreshIsHeadSegment(15, diffAt(15))
    }

    @Test
    fun jumpToTheSecondLastRowStillMovesTheHeadSegmentToTheTail() {
        assertFreshIsHeadSegment(19, diffAt(19))
    }

    @Test
    fun playingPreviousOnlyFadesInTheNewHead() {
        val result = diffAt(20)

        // 「上一首」：只有新队首换新 key，其余整体下移一位并沿用旧 key
        assertEquals(listOf("a20"), result.freshIds())
        assertEquals("old:a0", result.keyOf("a0"))
        assertEquals("old:a19", result.keyOf("a19"))
    }

    @Test
    fun legacyDiffFlipsAtHalfwayWhichIsWhyThisExists() {
        val old = oldList(*ids.toTypedArray())
        val legacyAt10 = old.diff(rotate(10), getId = { it.id }, isSameItem = sameId, isSameContent = sameContent)
        val legacyAt15 = old.diff(rotate(15), getId = { it.id }, isSameItem = sameId, isSameContent = sameContent)
        val legacyAt20 = old.diff(rotate(20), getId = { it.id }, isSameItem = sameId, isSameContent = sameContent)

        // 过半之前：旧 diff 也保留旧尾块，效果符合预期
        assertEquals(ids.take(10), legacyAt10.freshIds())
        // 过半之后：翻转成保留旧前块、把旧尾块挪到队首（新 key 落在 a15..a20）
        assertEquals(ids.drop(15), legacyAt15.freshIds())
        // 「上一首」：旧 diff 本就保留旧前块，所以这个方向上行为不变（本次改动不碰它）
        assertEquals(ids.drop(20), legacyAt20.freshIds())
        assertEquals(ids.drop(20), diffAt(20).freshIds())
    }

    @Test
    fun jumpedRowKeepsItsKeySoTheScrollAnchorSurvives() {
        // p=15：当前播放的 a15、以及可见的 a20 都必须保留旧 key，
        // 否则 LazyColumn 的滚动锚点会跟着「上移的旧前块」跑到列表末尾。
        val result = diffAt(15)

        assertEquals("old:a15", result.keyOf("a15"))
        assertEquals("old:a20", result.keyOf("a20"))
    }

    @Test
    fun contentChangeKeepsTheOldKey() {
        val old = oldList("a0", "a1", "a2")
        val new = listOf(Row("a0"), Row("a1", title = "changed"), Row("a2"))

        val result = old.rotationalDiff(new, getId = { it.id }, isSameItem = sameId, isSameContent = sameContent)

        assertEquals(emptyList(), result.freshIds())
        assertEquals("old:a1", result.keyOf("a1"))
        assertEquals("changed", result.first { it.data.id == "a1" }.data.title)
    }

    @Test
    fun removalFallsBackToLegacyDiff() {
        val old = oldList("a0", "a1", "a2", "a3")
        assertSameAsLegacyDiff(old, listOf(Row("a0"), Row("a1"), Row("a3")))
    }

    @Test
    fun appendedItemFallsBackToLegacyDiff() {
        val old = oldList("a0", "a1", "a2")
        assertSameAsLegacyDiff(old, listOf("a0", "a1", "a2", "a3").map { Row(it) })
    }

    @Test
    fun reorderedNonRotationFallsBackToLegacyDiff() {
        val old = oldList("a0", "a1", "a2", "a3")
        // 队首是 a2，但其余顺序不构成「左旋 2 位」
        assertSameAsLegacyDiff(old, listOf("a2", "a1", "a0", "a3").map { Row(it) })
    }

    @Test
    fun duplicatedIdFallsBackToLegacyDiff() {
        // 队首 id 在旧列表中出现两次，无法确定切点
        val old = oldList("a0", "a0", "a1")
        assertSameAsLegacyDiff(old, listOf("a0", "a1", "a0").map { Row(it) })
    }

    @Test
    fun emptySideFallsBackToLegacyDiff() {
        val old = oldList("a0", "a1")
        assertSameAsLegacyDiff(old, emptyList())
        assertSameAsLegacyDiff(emptyList(), listOf(Row("a0")))
    }

    @Test
    fun singleItemListKeepsItsKey() {
        val old = oldList("a0")
        val result = old.rotationalDiff(listOf(Row("a0")), getId = { it.id }, isSameItem = sameId, isSameContent = sameContent)

        assertEquals(emptyList(), result.freshIds())
        assertEquals("old:a0", result.keyOf("a0"))
    }

    @Test
    fun singleItemContentChangeKeepsItsKey() {
        val old = oldList("a0")
        val new = listOf(Row("a0", "changed"))
        val result = old.rotationalDiff(new, getId = { it.id }, isSameItem = sameId, isSameContent = sameContent)

        assertEquals("old:a0", result.keyOf("a0"))
        assertEquals("changed", result.single().data.title)
    }

    @Test
    fun everyRotationMatchesTheNewOrderAndReplacesTheWholeHeadSegment() {
        for (p in ids.indices) {
            val expectedFresh = if (p == ids.lastIndex) listOf(ids.last()) else ids.take(p)
            val result = diffAt(p)

            assertEquals(rotate(p).map { it.id }, result.ids(), "p=$p 返回顺序应与新列表一致")
            assertEquals(expectedFresh, result.freshIds(), "p=$p 换新 key 的元素不符")
        }
    }
}

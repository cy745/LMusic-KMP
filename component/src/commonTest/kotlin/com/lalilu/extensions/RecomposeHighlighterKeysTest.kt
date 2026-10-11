package com.lalilu.extensions

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * [RecomposeHighlighterKeys] 的避让逻辑测试：同一角落的多个标签必须自动错行、互不重叠。
 */
class RecomposeHighlighterKeysTest {

    private val labelSize = Size(width = 60f, height = 20f)
    private val gap = 4f

    /** 与实现里一致的候选矩形：0 行最靠近左下角，逐行往上。 */
    private fun candidate(row: Int, bottom: Float = 200f, left: Float = 10f) = Rect(
        offset = Offset(left, bottom - labelSize.height - row * (labelSize.height + gap)),
        size = labelSize,
    )

    @Test
    fun labelsAtTheSameCornerStackWithoutOverlap() {
        val first = "first"
        val second = "second"

        val firstRect = RecomposeHighlighterKeys.place(first) { row -> candidate(row) }
        val secondRect = RecomposeHighlighterKeys.place(second) { row -> candidate(row) }

        assertFalse(firstRect.overlaps(secondRect), "同一角落的两个标签不应重叠")
        assertEquals(candidate(0), firstRect, "先登记的标签应该在 0 行")
        assertEquals(candidate(1), secondRect, "后登记的标签应该自动让到上一行")

        RecomposeHighlighterKeys.release(first)
        RecomposeHighlighterKeys.release(second)
    }

    @Test
    fun releasedLabelFreesItsRow() {
        val first = "first"
        val second = "second"

        RecomposeHighlighterKeys.place(first) { row -> candidate(row) }
        RecomposeHighlighterKeys.release(first)

        // 第一行空出来之后，后来的标签可以重新占用它
        val secondRect = RecomposeHighlighterKeys.place(second) { row -> candidate(row) }
        assertEquals(candidate(0), secondRect)

        RecomposeHighlighterKeys.release(second)
    }

    @Test
    fun labelsAtDifferentCornersDoNotAffectEachOther() {
        val left = "left"
        val right = "right"

        val leftRect = RecomposeHighlighterKeys.place(left) { row -> candidate(row, left = 10f) }
        val rightRect = RecomposeHighlighterKeys.place(right) { row -> candidate(row, left = 500f) }

        assertEquals(candidate(0, left = 10f), leftRect)
        assertEquals(candidate(0, left = 500f), rightRect, "不同角落的标签不需要错行")

        RecomposeHighlighterKeys.release(left)
        RecomposeHighlighterKeys.release(right)
    }
}

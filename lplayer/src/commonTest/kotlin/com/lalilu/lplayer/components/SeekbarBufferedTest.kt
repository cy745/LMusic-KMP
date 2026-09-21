package com.lalilu.lplayer.components

import com.lalilu.lmedia.domain.source.BufferedRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 已缓冲段的几何换算：
 * - 空区间/空列表不产生矩形，否则本地文件播放会多出一条恒为空的次级层
 * - 越界比例必须夹到轨道内，否则会画到圆角之外
 * - 每一段都要落在自己的位置上（跳转后的碎片化缓存必须画成多段）
 */
class SeekbarBufferedTest {

    @Test
    fun noRangesDrawsNothing() {
        assertEquals(emptyList(), bufferedBarGeometries(4f, 300f, emptyList()))
        assertEquals(
            emptyList(),
            bufferedBarGeometries(4f, 300f, listOf(BufferedRange(0.5f, 0.5f))),
            "零宽区间不画",
        )
    }

    @Test
    fun rangeMapsToTrackPosition() {
        assertEquals(
            listOf(BufferedBarGeometry(left = 154f, width = 150f)),
            bufferedBarGeometries(4f, 300f, listOf(BufferedRange(0.5f, 1f))),
        )
        assertEquals(
            listOf(BufferedBarGeometry(left = 4f, width = 300f)),
            bufferedBarGeometries(4f, 300f, listOf(BufferedRange(0f, 1f))),
        )
    }

    @Test
    fun fragmentedCoverageBecomesSeveralBars() {
        val geometries = bufferedBarGeometries(
            paddingValue = 0f,
            innerWidth = 400f,
            ranges = listOf(
                BufferedRange(0f, 0.25f),
                BufferedRange(0.75f, 1f),
            ),
        )

        assertEquals(
            listOf(
                BufferedBarGeometry(left = 0f, width = 100f),
                BufferedBarGeometry(left = 300f, width = 100f),
            ),
            geometries,
            "跳转后剩下的两段必须各画各的，中间的空洞留空",
        )
    }

    @Test
    fun outOfRangeFractionsAreClampedToTheTrack() {
        val geometries = bufferedBarGeometries(
            paddingValue = 0f,
            innerWidth = 300f,
            ranges = listOf(BufferedRange(-0.5f, 1.5f)),
        )

        assertEquals(listOf(BufferedBarGeometry(left = 0f, width = 300f)), geometries)
        assertTrue(
            geometries.all { it.left >= 0f && it.left + it.width <= 300f },
            "任何一段都不该越出轨道",
        )
    }
}

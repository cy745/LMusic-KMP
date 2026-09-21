package com.lalilu.lplayer.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 已缓冲条的几何计算：
 * - 没有缓冲时不绘制（返回 null），否则本地文件播放会多出一条恒为空的次级层
 * - 越界比例必须夹到轨道内，否则会画到圆角之外
 */
class SeekbarBufferedTest {
    @Test
    fun noBufferedFractionSkipsDrawing() {
        assertNull(bufferedBarGeometry(paddingValue = 4f, innerWidth = 300f, fraction = 0f))
        assertNull(bufferedBarGeometry(paddingValue = 4f, innerWidth = 300f, fraction = -0.5f))
    }

    @Test
    fun fractionMapsToTrackWidth() {
        assertEquals(
            BufferedBarGeometry(left = 4f, width = 150f),
            bufferedBarGeometry(paddingValue = 4f, innerWidth = 300f, fraction = 0.5f),
        )
        assertEquals(
            BufferedBarGeometry(left = 4f, width = 300f),
            bufferedBarGeometry(paddingValue = 4f, innerWidth = 300f, fraction = 1f),
        )
    }

    @Test
    fun overflowFractionClampsToTrack() {
        assertEquals(
            BufferedBarGeometry(left = 0f, width = 300f),
            bufferedBarGeometry(paddingValue = 0f, innerWidth = 300f, fraction = 1.5f),
        )
    }
}

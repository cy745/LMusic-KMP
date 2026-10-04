package com.lalilu.lplayer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.lalilu.lmedia.domain.source.BufferedRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 进度条次级层（已缓冲区间）的像素级验证。
 *
 * 画布内容没有语义树可断言，只能在黑色背景上采样像素：
 * - 已缓冲段应该比未缓冲段亮
 * - 没有缓冲信息（比例 0）时整条轨道亮度一致，即没有多画一条恒为空的次级层
 * - 次级层必须画在滑块之下，播放头左侧应保持滑块本身的颜色
 */
@OptIn(ExperimentalTestApi::class)
class SeekbarBufferedUiTest {
    private companion object {
        const val TAG = "seekbar"
    }

    @Composable
    private fun SeekbarHost(bufferedRanges: List<BufferedRange>, playbackPosition: Float) {
        MaterialTheme {
            Box(modifier = Modifier.size(400.dp, 120.dp).background(Color.Black)) {
                SeekbarLayout(
                    modifier = Modifier.fillMaxWidth().height(56.dp).testTag(TAG),
                    minValue = { 0f },
                    maxValue = { 100f },
                    dataValue = { playbackPosition },
                    bufferedRanges = { bufferedRanges },
                    animateColor = { Color(0xFFFF0000) },
                )
            }
        }
    }

    /** 在进度条水平方向的 [fraction] 处（垂直居中）取色。 */
    private fun ComposeUiTest.pixelAt(fraction: Float): Color {
        val image = onNodeWithTag(TAG).captureToImage()
        val pixels = image.toPixelMap()
        val x = (image.width * fraction).toInt().coerceIn(0, image.width - 1)
        return pixels[x, image.height / 2]
    }

    private fun Color.luminance(): Float = (red + green + blue) / 3f

    @Test
    fun bufferedLayerCoversTheBufferedPartOnly() = runDesktopComposeUiTest(width = 400, height = 120) {
        setContent { SeekbarHost(bufferedRanges = listOf(BufferedRange(0f, 0.6f)), playbackPosition = 0f) }

        val buffered = pixelAt(0.3f).luminance()
        val unbuffered = pixelAt(0.8f).luminance()

        assertTrue(
            buffered > unbuffered + 0.1f,
            "已缓冲段应明显亮于未缓冲段，实际 $buffered vs $unbuffered",
        )
    }

    @Test
    fun noBufferedProgressDrawsNothing() = runDesktopComposeUiTest(width = 400, height = 120) {
        setContent { SeekbarHost(bufferedRanges = emptyList(), playbackPosition = 0f) }

        assertEquals(
            pixelAt(0.3f).luminance(),
            pixelAt(0.8f).luminance(),
            0.001f,
            "比例 0 时不应绘制次级层",
        )
    }

    @Test
    fun bufferedLayerStaysUnderTheThumb() = runDesktopComposeUiTest(width = 400, height = 120) {
        setContent { SeekbarHost(bufferedRanges = listOf(BufferedRange(0f, 0.8f)), playbackPosition = 50f) }

        // 播放头在 50%，0.8 的缓冲层覆盖到这里；若绘制顺序反了，白色次级层会把滑块冲淡成粉色
        val overThumb = pixelAt(0.25f)
        assertTrue(
            overThumb.red > 0.9f && overThumb.green < 0.2f,
            "播放头左侧应保持滑块颜色，实际 $overThumb",
        )
    }

    @Test
    fun fragmentedCoverageLeavesTheHoleEmpty() = runDesktopComposeUiTest(width = 400, height = 120) {
        // 跳到 80% 之后的常态：头部一段与尾部一段已缓存，中间的 25%..75% 是空洞
        setContent {
            SeekbarHost(
                bufferedRanges = listOf(BufferedRange(0f, 0.25f), BufferedRange(0.75f, 1f)),
                playbackPosition = 0f,
            )
        }

        val head = pixelAt(0.1f).luminance()
        val hole = pixelAt(0.5f).luminance()
        val tail = pixelAt(0.9f).luminance()

        assertTrue(head > hole + 0.1f, "头部那段应亮于中间的洞，实际 $head vs $hole")
        assertTrue(tail > hole + 0.1f, "尾部那段应亮于中间的洞，实际 $tail vs $hole")
    }
}

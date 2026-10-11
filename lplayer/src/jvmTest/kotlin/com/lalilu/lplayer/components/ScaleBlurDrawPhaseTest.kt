package com.lalilu.lplayer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * [scaleBlur] 的两条契约：
 * 1. 半径在**绘制期**求值 —— 由状态驱动时只触发重绘，不会让宿主作用域重组
 *    （这是 BlurBackground「拖动展开不逐帧重组」修复的前提）；
 * 2. 半径为 0 时与「不挂这个 Modifier」渲染完全一致（不糊的时候不能改变画面）。
 */
@OptIn(ExperimentalTestApi::class)
class ScaleBlurDrawPhaseTest {

    @Test
    fun drawPhaseRadiusRepaintsWithoutRecomposingHost() = runComposeUiTest {
        val progress = mutableFloatStateOf(0f)
        var hostCompositions = 0

        mainClock.autoAdvance = false

        setContent {
            hostCompositions++
            Box(
                Modifier
                    .testTag("target")
                    .size(100.dp)
                    .background(Color.White)
                    // 半径只在绘制期求值
                    .scaleBlur(scale = 0.4f, radius = { (progress.floatValue * 20f).dp })
                    .drawBehind {
                        // 居中的黑色方块：模糊半径变大时，它的边缘会向外糊开
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(30f, 30f),
                            size = Size(40f, 40f),
                        )
                    }
            )
        }

        mainClock.advanceTimeByFrame()

        val compositionsBefore = hostCompositions
        // 方块左侧外 4px：半径 0 时是干净的白底
        val outsideBefore = onNodeWithTag("target").captureToImage().toPixelMap()[26, 50]
        assertEquals(Color.White, outsideBefore, "半径 0 时方块外侧应该还是白底")

        runOnUiThread { progress.floatValue = 1f }
        mainClock.advanceTimeByFrame()

        val outsideAfter = onNodeWithTag("target").captureToImage().toPixelMap()[26, 50]
        assertNotEquals(
            outsideBefore,
            outsideAfter,
            "半径在绘制期被重新读取，方块外侧应该被糊开（像素必须变化）",
        )
        assertEquals(
            compositionsBefore,
            hostCompositions,
            "只在绘制期读取的状态变化不应触发宿主重组（实际从 $compositionsBefore 涨到了 $hostCompositions）",
        )

        mainClock.autoAdvance = true
    }

    @Test
    fun zeroRadiusRendersIdenticallyToNoModifier() = runComposeUiTest {
        // 白底 + 居中 1px 黑线：任何额外的降采样/采样都会让它变灰或变宽
        fun content(): Modifier = Modifier
            .size(100.dp)
            .background(Color.White)
            .drawBehind {
                drawRect(color = Color.Black, topLeft = Offset(50f, 0f), size = Size(1f, size.height))
            }

        setContent {
            Box(content().testTag("withBlur").scaleBlur(radius = { 0.dp }, scale = 0.4f))
            Box(content().testTag("plain"))
        }

        val withBlur = onNodeWithTag("withBlur").captureToImage().toPixelMap()
        val plain = onNodeWithTag("plain").captureToImage().toPixelMap()

        assertEquals(plain.width, withBlur.width)
        (40..60).forEach { x ->
            assertEquals(
                plain[x, 50],
                withBlur[x, 50],
                "半径 0 时 x=$x 处应与不加 Modifier 完全一致",
            )
        }
    }
}

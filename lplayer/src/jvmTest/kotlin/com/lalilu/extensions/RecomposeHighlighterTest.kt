package com.lalilu.extensions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * [recomposeHighlighter] 的契约测试：
 * 1. 宿主作用域重组后，边框出现且颜色随窗口内计数变化；
 * 2. 静默超过 `resetAfter` 后计数归零，边框消失；
 * 3. 边框采样点取描边中心（`inset` 处正是 Stroke 的路径中心）。
 *
 * 计时相关的断言必须手动推时钟（`autoAdvance = false`），
 * 否则 `waitForIdle` 会把虚拟时间推过 `resetAfter`，计数还没被观察到就归零了。
 */
@OptIn(ExperimentalTestApi::class)
class RecomposeHighlighterTest {

    @Test
    fun countsRecompositionsWithinWindowThenResets() = runComposeUiTest {
        var recomposeTick by mutableIntStateOf(0)
        val inset = 20.dp

        mainClock.autoAdvance = false

        setContent {
            // 读取 tick，让下面这段可以在测试里被精确地重组
            recomposeTick
            Box(
                Modifier
                    .testTag("target")
                    .size(100.dp)
                    .background(Color.Black)
                    .recomposeHighlighter(strokeWidth = 8.dp, inset = inset)
            )
        }

        val node = onNodeWithTag("target").fetchSemanticsNode()
        val sample = with(node.layoutInfo.density) { inset.roundToPx() }

        fun borderColor(): Color =
            onNodeWithTag("target").captureToImage().toPixelMap()[sample, sample]

        // 首次组合不算重组：此时还没有计数，边框不该出现
        mainClock.advanceTimeByFrame()
        assertEquals(Color.Black, borderColor(), "第一次组合不应该被计为重组")

        // 第 1 次重组：边框出现
        runOnUiThread { recomposeTick++ }
        mainClock.advanceTimeByFrame()
        val afterFirst = borderColor()
        assertNotEquals(Color.Black, afterFirst, "重组后边框应该出现")

        // 窗口内继续重组：颜色应该随计数继续变化
        repeat(3) {
            runOnUiThread { recomposeTick++ }
            mainClock.advanceTimeByFrame()
        }
        val afterMore = borderColor()
        assertNotEquals(afterFirst, afterMore, "窗口内继续重组，边框颜色应随计数变化")

        // 静默超过窗口：计数归零，边框消失
        mainClock.advanceTimeBy(1_000)
        mainClock.advanceTimeByFrame()
        assertEquals(Color.Black, borderColor(), "静默超过 resetAfter 后计数应归零、边框应消失")

        mainClock.autoAdvance = true
    }

    @Test
    fun keyLabelIsDrawnAtTheBottomLeft() = runComposeUiTest {
        var recomposeTick by mutableIntStateOf(0)
        val inset = 20.dp

        mainClock.autoAdvance = false

        setContent {
            recomposeTick
            Box(
                Modifier
                    .testTag("labelled")
                    .size(100.dp)
                    .background(Color.White)   // 白底：标签是唯一的"非白"内容
                    .recomposeHighlighter(key = "outer", strokeWidth = 4.dp, inset = inset)
            )
        }

        mainClock.advanceTimeByFrame()
        runOnUiThread { recomposeTick++ }
        mainClock.advanceTimeByFrame()

        val pixels = onNodeWithTag("labelled").captureToImage().toPixelMap()
        val node = onNodeWithTag("labelled").fetchSemanticsNode()
        val band = with(node.layoutInfo.density) { (inset + 8.dp).roundToPx() }

        // 只看边框内侧的区域：边框本身在 inset 处，这里排除掉描边占用的宽度
        val insideHasContent = (band until pixels.height - band).any { y ->
            (band until pixels.width - band).any { x -> pixels[x, y] != Color.White }
        }
        assertTrue(insideHasContent, "左下角应该画出 key 标签（边框内侧应出现非背景色像素）")

        mainClock.autoAdvance = true
    }
}

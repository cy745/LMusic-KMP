package com.lalilu.lplayer.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [PlayingToolbar] 的点击手势契约。
 *
 * 这里守的是一个踩过的坑：toolbar 既要"吞掉触摸"（`isUserTouchEnable`，避免点击穿透到下层），
 * 又要识别双击。如果在同一个节点上再叠一个 `pointerInput { detectTapGestures }`，
 * 链尾的 `clickable` 会在 Main 阶段先派发并 `consume()` 掉按下事件，链首的双击识别器走
 * `awaitFirstDown(requireUnconsumed = true)`，于是永远等不到未消费的按下 —— 双击静默失效。
 * 单击与双击必须由同一个识别器处理，下面的用例就是这个约束的可执行形式。
 */
@OptIn(ExperimentalTestApi::class)
class PlayingToolbarGestureTest {

    @Test
    fun doubleClickTriggersOnDoubleClickAndNotOnClick() = runComposeUiTest {
        var singleClicks = 0
        var doubleClicks = 0

        setContent {
            PlayingToolbar(
                modifier = Modifier
                    .width(360.dp)
                    .height(72.dp)
                    .testTag("toolbar"),
                title = { "Title" },
                subtitle = { "Subtitle" },
                contentColor = { Color.Black },
                // 关键前置条件：触碰可用时 toolbar 会挂上消费型手势
                isUserTouchEnable = { true },
                onClick = { singleClicks++ },
                onDoubleClick = { doubleClicks++ },
            )
        }

        onNodeWithTag("toolbar").performTouchInput { doubleClick() }
        waitForIdle()

        assertEquals(1, doubleClicks, "双击应触发 onDoubleClick")
        assertEquals(0, singleClicks, "双击不应同时被计为单击")
    }

    @Test
    fun singleClickStillTriggersOnClick() = runComposeUiTest {
        var singleClicks = 0
        var doubleClicks = 0

        setContent {
            PlayingToolbar(
                modifier = Modifier
                    .width(360.dp)
                    .height(72.dp)
                    .testTag("toolbar"),
                title = { "Title" },
                subtitle = { "Subtitle" },
                contentColor = { Color.Black },
                isUserTouchEnable = { true },
                onClick = { singleClicks++ },
                onDoubleClick = { doubleClicks++ },
            )
        }

        onNodeWithTag("toolbar").performClick()
        // 代价说明：只要 onDoubleClick 存在，单击就得等双击判定窗口过去才能确认，
        // 因此 onClick 变为"延后触发"而非即时触发（当前调用方传的都是空实现，无实际影响）。
        waitUntil(timeoutMillis = 3_000) { singleClicks == 1 }
        waitForIdle()

        assertEquals(1, singleClicks, "单击应触发 onClick")
        assertEquals(0, doubleClicks, "单击不应触发 onDoubleClick")
    }

    @Test
    fun gesturesStayDisabledWhenTouchIsNotEnable() = runComposeUiTest {
        var singleClicks = 0
        var doubleClicks = 0

        setContent {
            PlayingToolbar(
                modifier = Modifier
                    .width(360.dp)
                    .height(72.dp)
                    .testTag("toolbar"),
                title = { "Title" },
                subtitle = { "Subtitle" },
                contentColor = { Color.Black },
                // 中间锚点等状态下 toolbar 不参与触摸，手势必须整体失效
                isUserTouchEnable = { false },
                onClick = { singleClicks++ },
                onDoubleClick = { doubleClicks++ },
            )
        }

        onNodeWithTag("toolbar").performTouchInput { doubleClick() }
        waitForIdle()

        assertEquals(0, doubleClicks, "触碰关闭时不应识别双击")
        assertEquals(0, singleClicks, "触碰关闭时不应识别单击")
    }
}

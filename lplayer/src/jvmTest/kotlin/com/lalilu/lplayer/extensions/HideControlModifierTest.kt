package com.lalilu.lplayer.extensions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [hideControl] 的行为验证。
 *
 * 重点覆盖移植时唯一改动的部分：单端用 Android 专属的 `pointerInteropFilter` 拦截首次点击，
 * 这里改为在 `PointerEventPass.Initial` 阶段 `consume()`，需要保证"隐藏时第一次点击只负责显示、
 * 显示之后的点击才触发内部回调"这一语义不变。
 */
@OptIn(ExperimentalTestApi::class)
class HideControlModifierTest {

    @Test
    fun interceptConsumesTheFirstClickAndPassesTheNextOne() = runComposeUiTest {
        var clicks = 0
        // hideDelay 取足够大，避免自动隐藏在断言之间发生
        setContent {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .hideControl(enable = { true }, intercept = { true }, hideDelay = 60_000L)
            ) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .testTag("target")
                        .clickable { clicks++ }
                )
            }
        }

        // 初始为隐藏态：第一次点击只应"显示出来"，不触发内部回调
        onNodeWithTag("target").performClick()
        waitForIdle()
        assertEquals(0, clicks, "隐藏态下的第一次点击应被拦截，不应触发内部回调")

        // 已经显示后再点击，应正常触发
        onNodeWithTag("target").performClick()
        waitForIdle()
        assertEquals(1, clicks, "显示之后的点击应正常触发内部回调")
    }

    @Test
    fun withoutInterceptClicksAlwaysReachTheContent() = runComposeUiTest {
        var clicks = 0
        // 进度条就是这么用的：不拦截，保证原有手势/点击不受影响
        setContent {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .hideControl(enable = { true }, hideDelay = 60_000L)
            ) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .testTag("target")
                        .clickable { clicks++ }
                )
            }
        }

        onNodeWithTag("target").performClick()
        waitForIdle()
        assertEquals(1, clicks, "intercept 为 false 时，隐藏态下的点击也应到达内容")
    }
}

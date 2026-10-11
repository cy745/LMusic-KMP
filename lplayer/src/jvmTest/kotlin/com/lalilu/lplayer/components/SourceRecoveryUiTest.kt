package com.lalilu.lplayer.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.lalilu.extensions.DynamicTipsHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Image
import java.io.File

@OptIn(ExperimentalTestApi::class)
class SourceRecoveryUiTest {
    @Test fun unavailableQueueRowAndCoverIgnoreClickAndLongPress() = runComposeUiTest {
        var actions = 0
        setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                SongCard(modifier = Modifier.testTag("song"), id = "a", title = "歌曲", enabled = false,
                    onClick = { actions++ }, onLongClick = { actions++ })
            }
        }
        onNodeWithTag("song").assertIsNotEnabled().performTouchInput { click(); longClick() }
        onNodeWithContentDescription("Song Card Image", useUnmergedTree = true)
            .assertIsNotEnabled().performTouchInput { click(); longClick() }
        assertEquals(0, actions)
    }

    @Test fun topNoticeStaysAcrossPageChangesAndCanBeDismissed() = runDesktopComposeUiTest(width = 360, height = 800) {
        val visible = mutableStateOf(true)
        val page = mutableStateOf("曲库")
        setContent {
            MaterialTheme {
                Surface {
                    Box(Modifier.fillMaxSize()) {
                        Text(page.value, Modifier.padding(top = 180.dp))
                        DynamicTipsHost(visible.value, "正在恢复数据源 · 15 秒",
                            "还有 2 个来源未就绪，灰色歌曲暂不可用。关闭后仍会继续恢复。",
                            onDismiss = { visible.value = false })
                    }
                }
            }
        }
        onNodeWithText("关闭").assertIsDisplayed()
        saveScreenshot("startup-tip-phone.png", captureToImage())
        runOnIdle { page.value = "专辑" }
        onNodeWithText("专辑").assertIsDisplayed()
        onNodeWithText("关闭").performClick()
        onNodeWithText("关闭").assertDoesNotExist()
        onNodeWithText("专辑").assertIsDisplayed()
    }

    @Test fun noticeHasBoundedWidthOnLargeScreens() = runDesktopComposeUiTest(width = 1200, height = 800) {
        setContent {
            MaterialTheme {
                Surface {
                    Box(Modifier.fillMaxSize()) {
                        DynamicTipsHost(true, "正在恢复数据源 · 8 秒",
                            "还有 1 个来源未就绪，灰色歌曲暂不可用。关闭后仍会继续恢复。", {})
                    }
                }
            }
        }
        onNodeWithText("关闭").assertIsDisplayed()
        assertTrue(onNodeWithTag("dynamic_tips").fetchSemanticsNode().boundsInRoot.width <= 460f)
        saveScreenshot("startup-tip-wide.png", captureToImage())
    }

    private fun saveScreenshot(name: String, bitmap: ImageBitmap) {
        val directory = System.getenv("LMUSIC_UI_SCREENSHOT_DIR") ?: return
        val target = File(directory).apply { mkdirs() }
        Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
            image.encodeToData()?.use { File(target, name).writeBytes(it.bytes) }
        }
    }
}

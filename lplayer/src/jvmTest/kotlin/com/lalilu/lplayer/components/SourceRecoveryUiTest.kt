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
import com.lalilu.lmedia.component.AudioItemCard
import com.lalilu.lmedia.LocalMediaSourceStatuses
import com.lalilu.lmedia.LocalPlaybackFailures
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.PlaybackFailure
import com.lalilu.lmedia.domain.model.PlaybackFailureReason
import com.lalilu.lmedia.domain.repository.SourceStatus
import com.lalilu.lmedia.domain.source.MediaContentAvailability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Image
import java.io.File

@OptIn(ExperimentalTestApi::class)
class SourceRecoveryUiTest {
    @Test fun sameRawIdFromDifferentSourcesKeepsIndependentFailureAndEnablement() = runComposeUiTest {
        val local = LAudio(id = "42", mediaSourceName = "local")
        val remote = local.copy(mediaSourceName = "remote")
        val enabled = mutableStateOf(true)
        val failures = mutableStateOf(mapOf(remote.playbackId to PlaybackFailure(PlaybackFailureReason.PermissionDenied, 1)))
        val plays = mutableListOf<String>()
        setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalInspectionMode provides true,
                    LocalMediaSourceStatuses provides mapOf(
                        "local" to SourceStatus(contentAvailability = MediaContentAvailability.Ready),
                        "remote" to SourceStatus(enabled = enabled.value, contentAvailability = MediaContentAvailability.Ready),
                    ),
                    LocalPlaybackFailures provides failures.value,
                ) {
                    Column {
                        listOf(local, remote).forEach { audio ->
                            AudioItemCard(modifier = Modifier.testTag(audio.mediaSourceName),
                                id = audio.playbackId, title = audio.mediaSourceName, subtitle = "同一个原始 ID",
                                imageData = audio, onPlay = { plays += audio.playbackId })
                        }
                    }
                }
            }
        }
        val message = "播放失败 · ${PlaybackFailureReason.PermissionDenied.displayMessage} · 点击重试"
        onNodeWithTag("local").assertIsEnabled().performClick()
        onNodeWithTag("remote").assertIsEnabled().performClick()
        assertEquals(listOf(local.playbackId, remote.playbackId), plays)
        onNodeWithText(message).assertIsDisplayed()
        runOnIdle { enabled.value = false }
        onNodeWithTag("remote").assertIsNotEnabled().performTouchInput { click(); longClick() }
        onNodeWithContentDescription("Cover for remote", useUnmergedTree = true)
            .assertIsNotEnabled().performTouchInput { click(); longClick() }
        onNodeWithTag("local").assertIsEnabled()
        onNodeWithText(message).assertDoesNotExist()
        assertEquals(2, plays.size)
        runOnIdle { enabled.value = true }
        onNodeWithText(message).assertIsDisplayed()
        runOnIdle { failures.value = emptyMap() }
        onNodeWithText(message).assertDoesNotExist()
        onNodeWithTag("remote").assertIsEnabled()
    }

    @Test fun failedSongCanRetryButUnreadySourceCannotAndRecoveryKeepsTheFailure() = runDesktopComposeUiTest(width = 360, height = 800) {
        val audio = LAudio(id = "a", mediaSourceName = "local")
        val ready = mutableStateOf(true)
        val failures = mutableStateOf(mapOf(audio.playbackId to PlaybackFailure(PlaybackFailureReason.Network, 1)))
        var plays = 0
        val message = "播放失败 · ${PlaybackFailureReason.Network.displayMessage} · 点击重试"
        setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalInspectionMode provides true,
                    LocalMediaSourceStatuses provides mapOf("local" to SourceStatus(
                        contentAvailability = if (ready.value) MediaContentAvailability.Ready else MediaContentAvailability.Uninitialized,
                    )),
                    LocalPlaybackFailures provides failures.value,
                ) {
                    Surface {
                        AudioItemCard(modifier = Modifier.testTag("song"), id = audio.playbackId,
                            title = "失败歌曲", subtitle = "测试来源", imageData = audio,
                            onPlay = { plays++ })
                    }
                }
            }
        }
        onNodeWithText(message).assertIsDisplayed()
        onNodeWithTag("song").assertIsEnabled().performClick()
        assertEquals(1, plays)
        saveScreenshot("failed-song-phone.png", captureToImage())
        runOnIdle { ready.value = false }
        onNodeWithText(message).assertDoesNotExist()
        onNodeWithTag("song").assertIsNotEnabled().performTouchInput { click() }
        assertEquals(1, plays)
        runOnIdle { ready.value = true }
        onNodeWithText(message).assertIsDisplayed()
        onNodeWithTag("song").assertIsEnabled()
        runOnIdle { failures.value = emptyMap() }
        onNodeWithText(message).assertDoesNotExist()
        onNodeWithTag("song").assertIsEnabled()
    }

    @Test fun unavailableLibraryRowAndCoverIgnoreClickAndLongPress() = runComposeUiTest {
        var actions = 0
        val enabled = mutableStateOf(false)
        setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                AudioItemCard(
                    modifier = Modifier.testTag("song"), id = "a", title = "歌曲", subtitle = "来源",
                    enabled = enabled.value,
                    onPlay = { actions++ }, onSelect = { actions++ }, onEnterSelect = { actions++ },
                    onNavigateToDetail = { actions++ },
                )
            }
        }
        onNodeWithTag("song").assertIsNotEnabled().performTouchInput { click(); longClick() }
        onNodeWithContentDescription("Cover for 歌曲", useUnmergedTree = true)
            .assertIsNotEnabled().performTouchInput { click(); longClick() }
        assertEquals(0, actions)
        runOnIdle { enabled.value = true }
        onNodeWithTag("song").assertIsEnabled().performTouchInput { click() }
        assertEquals(1, actions)
    }

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

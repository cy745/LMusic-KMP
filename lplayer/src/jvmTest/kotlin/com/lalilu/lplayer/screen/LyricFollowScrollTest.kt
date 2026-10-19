package com.lalilu.lplayer.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.lalilu.LocalFontFamily
import com.lalilu.common.kv.KVContext
import com.lalilu.common.kv.KVItem
import com.lalilu.common.kv.testing.InMemoryKVSaver
import com.lalilu.llyric.LyricItem
import com.lalilu.llyricview.LyricContent
import com.lalilu.llyricview.LyricLayout
import com.lalilu.llyricview.LyricSettings
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * 歌词列表跟随播放位置的端到端验证。
 *
 * 这里覆盖的是最容易静默失效的一环：时间前进后列表必须真的滚动到正在唱的那一行。
 * 之前的状态机把"能不能滚"绑在过渡是否结束、手动模式是否刚退出这类瞬时状态上，
 * 一旦其中一个标志停住不动，高亮还在走、列表却再也不滚——从界面上只能看出"卡住了"。
 */
@OptIn(ExperimentalTestApi::class)
class LyricFollowScrollTest {

    @BeforeTest
    fun setup() {
        startKoin {
            modules(
                module {
                    single { Json { ignoreUnknownKeys = true } }
                    single<KVItem<LyricSettings>>(named("LyricSettings")) {
                        KVContext.obtainStatic(
                            key = "LyricFollowScrollTest",
                            defaultValue = LyricSettings(),
                            saver = InMemoryKVSaver(mutableMapOf()),
                        )
                    }
                }
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    private fun lyrics(count: Int): List<LyricItem> = (0 until count).map { index ->
        LyricItem.NormalLyric(
            content = "line $index",
            time = index * 1_000L,
            key = "line-$index",
        )
    }

    @Test
    fun listFollowsThePlayingLineAsTimeAdvances() = runComposeUiTest {
        val time = mutableStateOf(0L)
        val content = mutableStateOf<LyricContent>(
            LyricContent.Ready(key = "a", items = lyrics(80))
        )

        setContent {
            // 歌词用主题字体渲染：测试里给系统默认字体，避免为了排版把整套主题拉进来
            CompositionLocalProvider(LocalFontFamily provides FontFamily.Default) {
                Box(modifier = Modifier.size(width = 360.dp, height = 640.dp)) {
                    LyricLayout(
                        currentTime = { time.value },
                        lyricContent = content,
                        sampledPlaybackKey = { content.value.key },
                        // 与测试画布同尺寸（px）：上下边缘渐隐的留白按屏高三分之一算
                        screenConstraints = Constraints(maxWidth = 360, maxHeight = 640),
                    )
                }
            }
        }

        // 初始位置：第一行可见，后面的行还没滚进来
        onNodeWithText("line 0").assertIsDisplayed()

        runOnIdle { time.value = 60_000L }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("line 60").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("line 60").assertIsDisplayed()
    }

    @Test
    fun listKeepsFollowingAfterSwitchingToAnotherSong() = runComposeUiTest {
        val time = mutableStateOf(0L)
        val content = mutableStateOf<LyricContent>(
            LyricContent.Ready(key = "a", items = lyrics(80))
        )

        setContent {
            CompositionLocalProvider(LocalFontFamily provides FontFamily.Default) {
                Box(modifier = Modifier.size(width = 360.dp, height = 640.dp)) {
                    LyricLayout(
                        currentTime = { time.value },
                        lyricContent = content,
                        sampledPlaybackKey = { content.value.key },
                        screenConstraints = Constraints(maxWidth = 360, maxHeight = 640),
                    )
                }
            }
        }

        runOnIdle { time.value = 30_000L }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("line 30").fetchSemanticsNodes().isNotEmpty()
        }

        // 切歌：歌词文档整体换一份，播放键也跟着变
        runOnIdle {
            content.value = LyricContent.Ready(key = "b", items = lyrics(80))
            time.value = 0L
        }
        waitForIdle()
        runOnIdle { time.value = 60_000L }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("line 60").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("line 60").assertIsDisplayed()
    }
}

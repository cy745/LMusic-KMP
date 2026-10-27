@file:OptIn(kotlin.time.ExperimentalTime::class)
package com.lalilu.lhistory.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.lalilu.lhistory.entity.*
import com.lalilu.lhistory.viewmodel.*
import com.lalilu.navigation.smartbar.LongClickActionItemContent
import com.lalilu.navigation.smartbar.NavigatorHeader
import com.lalilu.preview.preview
import kotlinx.datetime.*
import org.jetbrains.skia.Image
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class HistoryUiTest {
    @BeforeTest fun setup() { startKoin {} }
    @AfterTest fun teardown() { stopKoin() }
    @Test fun chartSelectionAndPeriodSwitchWorkOnPhone() = runDesktopComposeUiTest(width = 400, height = 850) {
        val period = mutableStateOf(StatsPeriod.Week)
        val date = LocalDate(2026, 10, 27)
        val calendar = HistoryCalendar(date, TimeZone.UTC)
        setContent {
            preview(isDarkMode = true) {
                Box(Modifier.fillMaxSize()) {
                    Column {
                        NavigatorHeader(title = "历史记录", subTitle = "听过的音乐，留下的时光")
                        ListeningStatistics(HistoryStatistics(ListeningStats(42 * 60000), ListeningStats(198 * 60000, 56, 38),
                            calendar.bars(period.value, listOf(DailyListening(calendar.range(HistoryFilter.Week).from, 156 * 60000),
                                DailyListening(calendar.range(HistoryFilter.Today).from, 42 * 60000)))),
                            period.value, date, { period.value = it })
                    }
                }
            }
        }
        onNodeWithText("今日收听").assertIsDisplayed()
        onNodeWithContentDescription("2026-10-26，收听2 小时 36 分").performClick()
        onNodeWithText("10/26 · 2 小时 36 分").assertIsDisplayed()
        System.getenv("LMUSIC_UI_SCREENSHOT_DIR")?.let { path ->
            val file = File(path, "history-statistics-phone.png"); file.parentFile.mkdirs()
            Image.makeFromBitmap(captureToImage().asSkiaBitmap()).use { image ->
                image.encodeToData()?.use { file.writeBytes(it.bytes) }
            }
        }
        onNodeWithText("本月").performClick()
        onNodeWithText("本月不同歌曲").assertIsDisplayed()
        onNodeWithContentDescription("2026-10-31，收听0 分钟").assertExists()
        onNodeWithText("本周").performClick()
        onNodeWithText("本周不同歌曲").assertIsDisplayed()
    }
    @Test fun clearActionRequiresLongPressRatherThanOrdinaryTap() = runDesktopComposeUiTest {
        var count = 0
        setContent { MaterialTheme {
            LongClickActionItemContent(Modifier.padding(20.dp), color = Color.Red,
                title = "清除记录", subTitle = "长按清除全部历史与收听统计", onAction = { count++ })
        } }
        onNodeWithText("清除记录").performClick()
        runOnIdle { assertEquals(0, count) }
        onNodeWithText("清除记录").performTouchInput { down(center) }
        mainClock.advanceTimeBy(3000)
        onNodeWithText("清除记录").performTouchInput { up() }
        runOnIdle { assertEquals(1, count) }
    }
}

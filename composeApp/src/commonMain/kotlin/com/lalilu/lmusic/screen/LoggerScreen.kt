package com.lalilu.lmusic.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Severity
import com.lalilu.RemixIcon
import com.lalilu.extensions.PassThroughHelper
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lmusic.util.MemoryLogItem
import com.lalilu.lmusic.util.MemoryLogWriter
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenInfo
import com.lalilu.navigation.ScreenInfoFactory
import com.lalilu.remixicon.Development
import com.lalilu.remixicon.development.terminalLine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Destination("/log")
object LoggerScreen : Screen, ScreenInfoFactory {

    @Composable
    override fun provideScreenInfo(): ScreenInfo = remember {
        ScreenInfo(
            title = { "日志" },
            icon = RemixIcon.Development.terminalLine
        )
    }

    @Composable
    override fun Content() {
        LoggerScreenContent()
    }
}

@Composable
fun LoggerScreenContent() {
    val state = rememberLazyListState()

    val statusBar = WindowInsets.statusBars
    val statusBarPadding = statusBar.asPaddingValues()
    val navigationBar = WindowInsets.navigationBars.asPaddingValues()
    val smartBarHeight = PassThroughHelper.getValue(
        key = "SmartBarHeight",
        default = { navigationBar.calculateBottomPadding() }
    )

    LaunchedEffect(MemoryLogWriter.logs.size) {
        state.requestScrollToItem(MemoryLogWriter.logs.size - 1)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(
            top = statusBarPadding.calculateTopPadding() + 16.dp,
            bottom = smartBarHeight() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = MemoryLogWriter.logs,
            key = { it.index }
        ) {
            LogItem(
                modifier = Modifier.animateItem(),
                logItem = it
            )
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
fun LogItem(
    modifier: Modifier = Modifier,
    logItem: MemoryLogItem
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val string = remember(logItem, textColor, secondaryTextColor) {
        val time = Instant.fromEpochMilliseconds(logItem.timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        val severityColor = when (logItem.severity) {
            Severity.Verbose -> Color.Gray
            Severity.Debug -> Color.Blue
            Severity.Info -> Color(0xFF30FF30)
            Severity.Warn -> Color.Yellow
            Severity.Error -> Color.Red
            Severity.Assert -> Color.Red
        }

        buildAnnotatedString {
            append("[${time.hour}:${time.minute}:${time.second}.${time.nanosecond % 10000}]")

            withStyle(style = SpanStyle(color = severityColor)) {
                append("[${logItem.severity.name}]")
            }
            withStyle(style = SpanStyle(color = secondaryTextColor)) {
                append("[${logItem.tag}]")
            }
            append('\n')
            withStyle(style = SpanStyle(color = textColor)) {
                append(logItem.message)
            }
            if (logItem.throwable != null) {
                append('\n')
                withStyle(style = SpanStyle(color = secondaryTextColor)) {
                    append(logItem.throwable.stackTraceToString())
                }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = string,
            color = textColor,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}
package com.lalilu.lmusic.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lmusic.util.MemoryLogItem
import com.lalilu.lmusic.util.MemoryLogWriter
import com.lalilu.navigation.Screen
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Destination("/log")
object LoggerScreen : Screen {
    @Composable
    override fun Content() {
        LoggerScreenContent()
    }
}

@Composable
fun LoggerScreenContent() {
    val state = rememberLazyListState()

    LaunchedEffect(MemoryLogWriter.logs.size) {
        state.requestScrollToItem(MemoryLogWriter.logs.size - 1)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(20.dp),
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
@Preview
@Composable
fun LogItem(
    modifier: Modifier = Modifier,
    logItem: MemoryLogItem
) {
    val string = remember(logItem) {
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
            withStyle(style = SpanStyle(color = Color.DarkGray)) {
                append("[${logItem.tag}]")
            }
            append('\n')
            withStyle(style = SpanStyle(color = Color.Black)) {
                append(logItem.message)
            }
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = string,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}
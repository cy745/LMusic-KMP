package com.lalilu.lhistory.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.lhistory.viewmodel.*
import kotlinx.datetime.LocalDate
import kotlin.math.ceil

@Composable
internal fun QuietChoice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.heightIn(min = 48.dp).clickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null,
        role = Role.Tab, onClick = onClick,
    ).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 1f else .55f))
    }
}

@Composable
internal fun ListeningStatistics(
    statistics: HistoryStatistics, period: StatsPeriod, today: LocalDate, onPeriod: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("收听统计", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            StatsPeriod.entries.forEach { QuietChoice(it.label, it == period, { onPeriod(it) }) }
        }
        val cards = listOf("今日收听" to listeningTime(statistics.today.duration),
            "${period.label}收听" to listeningTime(statistics.period.duration),
            "${period.label}播放" to "${statistics.period.playCount} 次",
            "${period.label}不同歌曲" to "${statistics.period.songCount} 首")
        BoxWithConstraints {
            val columns = if (maxWidth >= 600.dp) 4 else 2
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cards.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (label, value) ->
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .045f))
                                .padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
                                Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
        ListeningChart(statistics.bars, period, today)
    }
}

@Composable
internal fun ListeningChart(bars: List<ListeningBar>, period: StatsPeriod, today: LocalDate) {
    var selected by remember(period, today) { mutableStateOf(today) }
    val current = bars.firstOrNull { it.date == selected }
    val foreground = MaterialTheme.colorScheme.onSurface
    val maximum = bars.maxOfOrNull { it.duration } ?: 0L
    val step = if (maximum <= 3600000) 15 * 60000L else 3600000L
    val ceiling = (ceil(maximum.toDouble() / step).toLong().coerceAtLeast(1) * step).coerceAtLeast(60000)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("每日收听", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = foreground.copy(alpha = .65f))
            Text(current?.let { "${it.date.monthNumber}/${it.date.dayOfMonth} · ${listeningTime(it.duration)}" } ?: "暂无收听数据",
                fontSize = 12.sp, color = foreground.copy(alpha = .65f))
        }
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(38.dp).height(144.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(ceiling, ceiling * 2 / 3, ceiling / 3, 0L).forEach {
                    Text(if (it == 0L) "0" else if (it >= 3600000 && it % 3600000 == 0L) "${it / 3600000} 时" else "${it / 60000} 分",
                        fontSize = 10.sp, color = foreground.copy(alpha = .5f))
                }
            }
            Box(Modifier.weight(1f).height(176.dp)) {
                Canvas(Modifier.fillMaxWidth().height(144.dp)) {
                    repeat(4) { drawLine(foreground.copy(alpha = .08f), Offset(0f, size.height * it / 3),
                        Offset(size.width, size.height * it / 3), 1.dp.toPx()) }
                }
                Row(Modifier.fillMaxSize()) {
                    bars.forEachIndexed { index, bar ->
                        val fraction by animateFloatAsState((bar.duration.toFloat() / ceiling).coerceIn(0f, 1f))
                        Column(Modifier.weight(1f).fillMaxHeight().semantics {
                            contentDescription = "${bar.date}，收听${listeningTime(bar.duration)}"
                        }.clickable(role = Role.Button, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            selected = bar.date
                        }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.height(144.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                                if (bar.duration > 0) Box(Modifier.fillMaxWidth(if (period == StatsPeriod.Week) .5f else .65f)
                                    .fillMaxHeight(fraction).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(foreground.copy(alpha = if (bar.date == selected) .75f else .35f)))
                            }
                            val label = if (period == StatsPeriod.Week) listOf("一", "二", "三", "四", "五", "六", "日")[bar.date.dayOfWeek.ordinal]
                                else if (bar.date.dayOfMonth == 1 || bar.date.dayOfMonth % 5 == 0 || index == bars.lastIndex) "${bar.date.dayOfMonth}" else ""
                            Text(label, Modifier.padding(top = 8.dp), fontSize = 10.sp,
                                color = foreground.copy(alpha = if (bar.date == selected) 1f else .5f), maxLines = 1)
                        }
                    }
                }
            }
        }
        Text("点选柱子查看时长 · 暂停和缓冲不计入", fontSize = 11.sp, color = foreground.copy(alpha = .5f))
    }
}

@file:OptIn(kotlin.time.ExperimentalTime::class)
package com.lalilu.lhistory.viewmodel

import com.lalilu.lhistory.entity.DailyListening
import com.lalilu.lhistory.entity.ListeningStats
import kotlinx.datetime.*
import kotlin.time.Instant

enum class StatsPeriod(val label: String) { Week("本周"), Month("本月") }
enum class HistoryFilter(val label: String) { All("全部"), Today("今天"), Week("本周"), Month("本月") }
data class HistoryCalendar(val today: LocalDate, val zone: TimeZone)
data class DateRange(val from: Long, val until: Long)
data class ListeningBar(val date: LocalDate, val duration: Long)
data class HistoryStatistics(
    val today: ListeningStats = ListeningStats(), val period: ListeningStats = ListeningStats(),
    val bars: List<ListeningBar> = emptyList(),
)
fun HistoryCalendar.range(filter: HistoryFilter): DateRange {
    val start = when (filter) {
        HistoryFilter.All -> return DateRange(Long.MIN_VALUE, Long.MAX_VALUE)
        HistoryFilter.Today -> today
        HistoryFilter.Week -> today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
        HistoryFilter.Month -> LocalDate(today.year, today.month, 1)
    }
    return DateRange(start.atStartOfDayIn(zone).toEpochMilliseconds(),
        today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds())
}
fun HistoryCalendar.bars(period: StatsPeriod, rows: List<DailyListening>): List<ListeningBar> {
    val start = if (period == StatsPeriod.Week) today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
        else LocalDate(today.year, today.month, 1)
    val end = if (period == StatsPeriod.Week) start.plus(7, DateTimeUnit.DAY) else start.plus(1, DateTimeUnit.MONTH)
    val values = rows.associate { Instant.fromEpochMilliseconds(it.dayStart).toLocalDateTime(zone).date to it.duration }
    return generateSequence(start) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it < end }
        .map { ListeningBar(it, values[it] ?: 0) }.toList()
}
fun listeningTime(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return when {
        seconds == 0L -> "0 分钟"
        seconds < 60 -> "$seconds 秒"
        seconds < 3600 -> "${seconds / 60} 分钟"
        else -> "${seconds / 3600} 小时 ${seconds % 3600 / 60} 分"
    }
}
fun historyDayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "今天 · ${date.monthNumber} 月 ${date.dayOfMonth} 日"
    today.minus(1, DateTimeUnit.DAY) -> "昨天 · ${date.monthNumber} 月 ${date.dayOfMonth} 日"
    else -> "${date.year} 年 ${date.monthNumber} 月 ${date.dayOfMonth} 日"
}

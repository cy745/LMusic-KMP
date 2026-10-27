@file:OptIn(kotlin.time.ExperimentalTime::class)
package com.lalilu.lhistory

import com.lalilu.lhistory.viewmodel.*
import com.lalilu.lhistory.entity.DailyListening
import kotlinx.datetime.*
import kotlin.test.*

class HistoryPresentationTest {
    @Test fun weekStartsOnMondayAndMissingDaysAreZeroFilled() {
        val calendar = HistoryCalendar(LocalDate(2026, 10, 27), TimeZone.UTC)
        val range = calendar.range(HistoryFilter.Week)
        val bars = calendar.bars(StatsPeriod.Week, listOf(DailyListening(range.from, 42000)))
        assertEquals(LocalDate(2026, 10, 26), bars.first().date)
        assertEquals(LocalDate(2026, 11, 1), bars.last().date)
        assertEquals(7, bars.size)
        assertEquals(42000L, bars.first().duration)
        assertTrue(bars.drop(1).all { it.duration == 0L })
    }
    @Test fun monthHandlesLeapYearAndLocalDstDay() {
        assertEquals(29, HistoryCalendar(LocalDate(2024, 2, 2), TimeZone.UTC).bars(StatsPeriod.Month, emptyList()).size)
        val spring = HistoryCalendar(LocalDate(2026, 3, 8), TimeZone.of("America/New_York")).range(HistoryFilter.Today)
        assertEquals(23 * 3600000L, spring.until - spring.from)
    }
}

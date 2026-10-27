package com.lalilu.lhistory.entity

import androidx.room3.Entity
import androidx.room3.Index

/** Actual listening allocated to the local calendar day, including sessions crossing midnight. */
@Entity(tableName = "history_day", primaryKeys = ["historyId", "dayStart"], indices = [Index(value = ["dayStart"])])
data class HistoryDay(val historyId: Long, val dayStart: Long, val duration: Long, val playCount: Int)

data class ListeningStats(val duration: Long = 0, val playCount: Int = 0, val songCount: Int = 0)

/** One chart bucket; the UI fills missing dates with zero rather than omitting them. */
data class DailyListening(val dayStart: Long, val duration: Long)

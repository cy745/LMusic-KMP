@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.lalilu.lhistory

import com.lalilu.lhistory.entity.*
import com.lalilu.lplayer.playback.ListeningObservation
import kotlinx.datetime.*
import kotlin.time.Instant

/** Single-writer session accounting. Clock inputs are captured before any database suspension. */
internal class ListeningRecorder(
    private val save: suspend (LHistory, List<HistoryDay>) -> Long,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    private var observation: ListeningObservation? = null
    private var lastMono = 0L
    private var record: LHistory? = null
    private val days = linkedMapOf<Long, HistoryDay>()
    private var finished = false
    private var cleared = false
    private var lastSavedDuration = 0L
    private var session = 0L
    private val pending = linkedMapOf<Long, Pair<LHistory, List<HistoryDay>>>()

    suspend fun accept(next: ListeningObservation, wall: Long, monotonic: Long) {
        val old = observation
        accrue(wall, (monotonic - lastMono).coerceAtLeast(0))
        val changed = old?.mediaId != next.mediaId || old?.epoch != next.epoch
        if (changed) {
            persist()
            cleared = false
            session++
            record = null; days.clear(); finished = false
            lastSavedDuration = 0
        }
        if (cleared && old?.playing == false && next.playing) {
            cleared = false; finished = false
        }
        observation = next
        lastMono = monotonic
        if (record == null && (next.requested || next.playing) && next.mediaId != null && !finished) {
            record = LHistory(contentId = requireNotNull(next.mediaId), contentTitle = next.title,
                startTime = wall, duration = 0)
            persist() // Selection updates recency immediately, even while loading.
        }
        if (next.title.isNotBlank()) record = record?.copy(contentTitle = next.title)
        if (next.playing && !finished) qualify(wall)
        if (next.completed) finished = true
        if (pending.isNotEmpty() || changed || old?.playing != next.playing || next.completed ||
            (record?.duration ?: 0) - lastSavedDuration >= 5000) persist()
    }

    /** Called in the same serialized inbox after the database clear succeeds. */
    fun cleared() {
        session++
        pending.clear(); record = null; days.clear(); lastSavedDuration = 0
        finished = true; cleared = true
    }

    private fun accrue(wall: Long, elapsed: Long) {
        if (observation?.playing != true || finished || record == null || elapsed == 0L) return
        val oldDuration = record!!.duration
        record = record!!.copy(duration = oldDuration + elapsed)
        var cursor = wall - elapsed
        while (cursor < wall) {
            val tz = zone()
            val date = Instant.fromEpochMilliseconds(cursor).toLocalDateTime(tz).date
            val start = date.atStartOfDayIn(tz).toEpochMilliseconds()
            val end = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds()
            val until = minOf(wall, end)
            val bucket = days[start] ?: HistoryDay(0, start, 0, 0)
            days[start] = bucket.copy(duration = bucket.duration + until - cursor)
            cursor = until
        }
    }

    private fun qualify(wall: Long) {
        val current = record ?: return
        if (current.playCount != 0) return
        record = current.copy(playCount = 1)
        val tz = zone()
        val start = Instant.fromEpochMilliseconds(wall).toLocalDateTime(tz).date.atStartOfDayIn(tz).toEpochMilliseconds()
        val bucket = days[start] ?: HistoryDay(0, start, 0, 0)
        days[start] = bucket.copy(playCount = 1)
    }

    private suspend fun persist() {
        record?.let { pending[session] = it to days.values.toList() }
        for ((key, snapshot) in pending.toMap()) {
            try {
                val id = save(snapshot.first, snapshot.second)
                if (key == session) {
                    record = record?.copy(id = id)
                    lastSavedDuration = snapshot.first.duration
                }
                pending.remove(key)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) {
                co.touchlab.kermit.Logger.w("Listening history retained for retry", error)
                break
            }
        }
    }
}

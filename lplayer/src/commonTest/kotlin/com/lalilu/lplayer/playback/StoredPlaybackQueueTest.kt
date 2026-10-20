package com.lalilu.lplayer.playback

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoredPlaybackQueueTest {
    @Test fun queueAndPositionRoundTripTogether() {
        val record = HistoryQueueIdentity(listOf("a", "b"), listOf("local", "remote"), 1)
            .toStoredQueue().copy(position = 42000L)
        assertEquals(record, Json.decodeFromString<StoredPlaybackQueue>(Json.encodeToString(record)))
    }

    @Test fun changingCurrentDoesNotReuseTheOldSongsPosition() {
        val original = HistoryQueueIdentity(listOf("a", "b"), listOf("local", "local"), 0)
        val record = original.toStoredQueue().copy(position = 42000L)
        assertEquals(0L, record.withIdentity(original.copy(index = 1)).position)
        assertEquals(42000L, record.withIdentity(original).position)
    }

    @Test fun anotherOccurrenceStartsAtZeroButInsertionBeforeCurrentKeepsPosition() {
        val original = HistoryQueueIdentity(listOf("a", "a"), listOf("local", "local"), 1)
        val record = original.toStoredQueue().copy(position = 42000L)
        assertEquals(0L, record.withIdentity(original.copy(index = 0)).position)
        assertEquals(42000L, record.withIdentity(HistoryQueueIdentity(
            listOf("b", "a", "a"), listOf("local", "local", "local"), 2,
        )).position)
    }

    @Test fun serializationPreservesSourceIdentityAndRepeatedOccurrence() {
        val identity = HistoryQueueIdentity(listOf("a:b", "a:b", "a:b"), listOf("local", "remote", "local"), 2)
        val serialized = Json.encodeToString(identity.toStoredQueue())
        assertEquals(identity, Json.decodeFromString<StoredPlaybackQueue>(serialized).resolve())
    }

    @Test fun malformedIdentityRejectsTheWholeQueueWithoutShiftingPositions() {
        assertNull(StoredPlaybackQueue(listOf("5:locala", "invalid"), 0).resolve())
        assertNull(StoredPlaybackQueue(listOf("5:locala"), 1).resolve())
    }

    @Test fun emptyQueueHasOneCanonicalIndex() {
        assertEquals(HistoryQueueIdentity(emptyList(), emptyList(), 0), StoredPlaybackQueue(emptyList(), 0).resolve())
        assertNull(StoredPlaybackQueue(emptyList(), 1).resolve())
    }

    /**
     * 恢复期队列可能"部分解析"：某些 slot 还没找到歌曲，来源名是 null。
     *
     * 以前这里直接 `requireNotNull` 抛 IllegalArgumentException —— 真机上就是一条
     * `FATAL EXCEPTION: Required value was null at saveQueueIdentity`。
     * 这种状态是合法的（id 要留着等下次扫描再解析），现在写成"未知来源"并能原样读回。
     */
    @Test fun unknownSourceRoundTripsAsUnknownInsteadOfThrowing() {
        val identity = HistoryQueueIdentity(listOf("a"), listOf(null), 0)

        val record = identity.toStoredQueue()

        assertEquals(identity, record.resolve(), "未知来源必须原样读回 null，而不是丢掉这一位")
        assertEquals(identity, Json.decodeFromString<StoredPlaybackQueue>(Json.encodeToString(record)).resolve())
    }
}

package com.lalilu.lplayer.playback

import com.lalilu.lplayer.LPlayerKV
import com.lalilu.lplayer.extensions.PlayMode
import org.koin.core.annotation.Single
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.lalilu.lmedia.domain.model.MediaKey

/** Disk queue uses a single reversible identity per slot, including repeated occurrences. */
@Serializable
internal data class StoredPlaybackQueue(val playbackIds: List<String>, val index: Int, val position: Long = 0L) {
    fun resolve(): HistoryQueueIdentity? {
        if (if (playbackIds.isEmpty()) index != 0 else index !in playbackIds.indices) return null
        val keys = playbackIds.map { MediaKey.parse(it) ?: return null }
        return HistoryQueueIdentity(keys.map { it.id }, keys.map { it.sourceName }, index)
    }

    fun withIdentity(identity: HistoryQueueIdentity): StoredPlaybackQueue {
        val next = identity.toStoredQueue()
        val current = playbackIds.getOrNull(index)
        val nextCurrent = next.playbackIds.getOrNull(next.index)
        val sameOccurrence = current != null && current == nextCurrent &&
            playbackIds.take(index).count { it == current } == next.playbackIds.take(next.index).count { it == current }
        return next.copy(position = if (sameOccurrence) position else 0L)
    }
}

internal fun HistoryQueueIdentity.toStoredQueue(): StoredPlaybackQueue {
    require(isValid())
    return StoredPlaybackQueue(ids.mapIndexed { index, id ->
        MediaKey(requireNotNull(sourceNames[index]), id).stableKey
    }, index)
}

@Serializable
data class HistoryQueueIdentity(
    val ids: List<String>,
    val sourceNames: List<String?>,
    val index: Int,
) {
    fun isValid(): Boolean = ids.size == sourceNames.size && (if (ids.isEmpty()) index == 0 else index in ids.indices)
}

/**
 * 历史记录存储抽象，与具体的 KV 实现解耦。
 */
interface HistoryStorage {
    fun readSnapshot(): PlaybackHistory.HistorySnapshot? = savedQueueIdentity()?.takeIf { it.isValid() && it.ids.isNotEmpty() }?.let {
        PlaybackHistory.HistorySnapshot(it.ids, it.index, savedPosition(), it.sourceNames)
    }
    fun autoPlayOnRestore(): Boolean = false
    fun savedQueueIdentity(): HistoryQueueIdentity? = null
    fun saveQueueIdentity(identity: HistoryQueueIdentity) = Unit
    fun saveSnapshot(identity: HistoryQueueIdentity, position: Long) {
        saveQueueIdentity(identity)
        savePosition(position)
    }
    fun savedPlaybackMode(): PlaybackMode = PlaybackMode.LOOP
    fun savedPosition(): Long

    fun savePosition(position: Long)
}

/**
 * [HistoryStorage] 的默认实现，桥接 [LPlayerKV]。
 * [LPlayerKV] 本身不感知此接口的存在，保持纯 KV 存取职责。
 */
@Single
class HistoryStorageImpl(
    private val kv: LPlayerKV = LPlayerKV
) : HistoryStorage {
    override fun autoPlayOnRestore(): Boolean = kv.autoPlayWhenRestart.value
    private val json = Json { ignoreUnknownKeys = true }
    private fun readRecord(): StoredPlaybackQueue? = runCatching {
        json.decodeFromString<StoredPlaybackQueue>(kv.historyPlaybackQueue.value)
    }.getOrNull()?.takeIf { it.resolve() != null && it.position >= 0L }

    override fun savedQueueIdentity(): HistoryQueueIdentity? = readRecord()?.resolve()

    override fun readSnapshot(): PlaybackHistory.HistorySnapshot? {
        val record = readRecord() ?: return null
        val identity = record.resolve() ?: return null
        if (identity.ids.isEmpty()) return null
        val position = if (kv.historyPositionResetRequested.value) {
            // A separate request cannot be overwritten by ongoing position samples. Consume it
            // only when a valid queue is actually read for restoration, keeping this reset on disk.
            kv.historyPlaybackQueue.value = json.encodeToString(record.copy(position = 0L))
            kv.historyPositionResetRequested.value = false
            0L
        } else record.position
        return PlaybackHistory.HistorySnapshot(identity.ids, identity.index, position, identity.sourceNames)
    }

    fun requestPositionReset() {
        kv.historyPositionResetRequested.value = true
    }

    override fun saveQueueIdentity(identity: HistoryQueueIdentity) {
        require(identity.isValid())
        val record = readRecord()?.withIdentity(identity) ?: identity.toStoredQueue()
        kv.historyPlaybackQueue.value = json.encodeToString(record)
    }
    override fun saveSnapshot(identity: HistoryQueueIdentity, position: Long) {
        kv.historyPlaybackQueue.value = json.encodeToString(identity.toStoredQueue().copy(position = position.coerceAtLeast(0L)))
    }
    override fun savedPlaybackMode(): PlaybackMode = when (PlayMode.from(kv.playMode.value)) {
        PlayMode.Sequential -> PlaybackMode.SEQUENTIAL
        PlayMode.ListRecycle -> PlaybackMode.LOOP
        PlayMode.RepeatOne -> PlaybackMode.SINGLE_LOOP
        PlayMode.Shuffle -> PlaybackMode.SHUFFLE
    }
    override fun savedPosition(): Long = if (kv.historyPositionResetRequested.value) 0L else readRecord()?.position ?: 0L

    override fun savePosition(position: Long) {
        val record = readRecord() ?: return
        kv.historyPlaybackQueue.value = json.encodeToString(record.copy(position = position.coerceAtLeast(0L)))
    }
}

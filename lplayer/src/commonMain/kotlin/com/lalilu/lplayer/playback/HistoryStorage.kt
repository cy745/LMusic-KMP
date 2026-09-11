package com.lalilu.lplayer.playback

import com.lalilu.lplayer.LPlayerKV
import com.lalilu.lplayer.extensions.PlayMode
import org.koin.core.annotation.Single
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class HistoryQueueIdentity(
    val ids: List<String>,
    val sourceNames: List<String?>,
    val index: Int,
) {
    fun isValid(): Boolean = ids.size == sourceNames.size && (ids.isEmpty() || index in ids.indices)
}

/**
 * 历史记录存储抽象，与具体的 KV 实现解耦。
 */
interface HistoryStorage {
    fun autoPlayOnRestore(): Boolean = false
    fun savedQueueIdentity(): HistoryQueueIdentity? = null
    fun saveQueueIdentity(identity: HistoryQueueIdentity) = Unit
    fun savedPlaybackMode(): PlaybackMode = PlaybackMode.LOOP
    fun savedPlaylistIds(): List<String>
    fun savedPlayId(): String
    fun savedPosition(): Long

    fun savePlaylistIds(ids: List<String>)
    fun savePlayId(id: String)
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
    override fun savedQueueIdentity(): HistoryQueueIdentity? = runCatching {
        json.decodeFromString<HistoryQueueIdentity>(kv.historyQueueIdentityV2.value)
    }.getOrNull()?.takeIf { it.isValid() }

    override fun saveQueueIdentity(identity: HistoryQueueIdentity) {
        require(identity.isValid())
        kv.historyQueueIdentityV2.value = json.encodeToString(identity)
    }
    override fun savedPlaybackMode(): PlaybackMode = when (PlayMode.from(kv.playMode.value)) {
        PlayMode.ListRecycle -> PlaybackMode.LOOP
        PlayMode.RepeatOne -> PlaybackMode.SINGLE_LOOP
        PlayMode.Shuffle -> PlaybackMode.SHUFFLE
    }
    override fun savedPlaylistIds(): List<String> = kv.historyPlaylistIds.value
    override fun savedPlayId(): String = kv.historyPlayId.value
    override fun savedPosition(): Long = kv.historyPlayPosition.value

    override fun savePlaylistIds(ids: List<String>) { kv.historyPlaylistIds.value = ids }
    override fun savePlayId(id: String) { kv.historyPlayId.value = id }
    override fun savePosition(position: Long) { kv.historyPlayPosition.value = position }
}

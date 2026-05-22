package com.lalilu.lplayer.playback

import com.lalilu.lplayer.LPlayerKV

/**
 * 历史记录存储抽象，与具体的 KV 实现解耦。
 */
interface HistoryStorage {
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
class HistoryStorageImpl(
    private val kv: LPlayerKV = LPlayerKV
) : HistoryStorage {
    override fun savedPlaylistIds(): List<String> = kv.historyPlaylistIds.value
    override fun savedPlayId(): String = kv.historyPlayId.value
    override fun savedPosition(): Long = kv.historyPlayPosition.value

    override fun savePlaylistIds(ids: List<String>) { kv.historyPlaylistIds.value = ids }
    override fun savePlayId(id: String) { kv.historyPlayId.value = id }
    override fun savePosition(position: Long) { kv.historyPlayPosition.value = position }
}

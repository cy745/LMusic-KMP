package com.lalilu.lplayer

import com.lalilu.common.kv.KVContext
import com.lalilu.lplayer.extensions.PlayMode

object LPlayerKV : KVContext("lplayer") {
    val historyPlayPosition = obtain("historyPlayPosition", defaultValue = 0L)
    val autoPlayWhenRestart = obtain("autoPlayWhenRestart", false)
    val handleBecomeNoisy = obtain("handleBecomeNoisy", true)
    val handleAudioFocus = obtain("handleAudioFocus", true)
    val historyPlaylistIds = obtainList("historyPlaylistIds", emptyList<String>())
    val historyPlaylistParentIds = obtainList("historyPlaylistParentIds", emptyList<String>())
    val historyPlayId = obtain("historyPlayId", "")
    val playMode = obtain("playMode", PlayMode.ListRecycle.name)
}
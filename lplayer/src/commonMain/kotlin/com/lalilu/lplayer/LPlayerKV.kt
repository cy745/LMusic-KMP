package com.lalilu.lplayer

import com.lalilu.common.kv.KVContext
import com.lalilu.lplayer.extensions.PlayMode

object LPlayerKV : KVContext("lplayer") {
    val autoPlayWhenRestart = obtain("autoPlayWhenRestart", false)
    val handleBecomeNoisy = obtain("handleBecomeNoisy", true)
    val handleAudioFocus = obtain("handleAudioFocus", true)
    val historyPlaybackQueue = obtain("historyPlaybackQueue", "")
    val historyPositionResetRequested = obtain("historyPositionResetRequested", false)
    val playMode = obtain("playMode", PlayMode.ListRecycle.name)
}

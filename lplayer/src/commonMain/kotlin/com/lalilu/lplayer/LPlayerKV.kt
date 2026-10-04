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

    /** 在设置页开启后，任意页面右上角显示加载过渡测试面板；默认关闭。 */
    val debugFloatingPanel = obtain("debugFloatingPanel", false)
}

package com.lalilu.lplayer

import com.lalilu.common.kv.KVContext
import com.lalilu.lplayer.extensions.PlayMode

object LPlayerKV : KVContext("lplayer") {
    val autoPlayWhenRestart = obtain("autoPlayWhenRestart", false)
    val handleBecomeNoisy = obtain("handleBecomeNoisy", true)
    val handleAudioFocus = obtain("handleAudioFocus", true)
    val historyPlaybackQueue = obtain("historyPlaybackQueue", "")

    /**
     * 歌词页展开时隐藏其他组件（toolbar / 进度条 / 系统状态栏）。
     *
     * 移植自单端 LMusic 的 `SettingsSp.autoHideSeekbar`，默认值与原实现一致为 false。
     */
    val autoHideSeekbar = obtain("autoHideSeekbar", false)
    val historyPositionResetRequested = obtain("historyPositionResetRequested", false)
    val playMode = obtain("playMode", PlayMode.ListRecycle.name)
}

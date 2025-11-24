package com.lalilu.lplayer.extensions

import androidx.media3.common.Player
import com.lalilu.lplayer.extensions.PlayMode.ListRecycle
import com.lalilu.lplayer.extensions.PlayMode.RepeatOne
import com.lalilu.lplayer.extensions.PlayMode.Shuffle


fun playModeOf(repeatMode: Int, shuffleModeEnabled: Boolean): PlayMode {
    if (repeatMode == Player.REPEAT_MODE_ONE) return RepeatOne
    if (shuffleModeEnabled) return Shuffle
    return ListRecycle
}

var Player.playMode
    get() = playModeOf(repeatMode, shuffleModeEnabled)
    set(value) {
        shuffleModeEnabled = value == Shuffle
        repeatMode = if (value == RepeatOne) Player.REPEAT_MODE_ONE
        else Player.REPEAT_MODE_ALL
    }
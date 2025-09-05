package com.lalilu.lplayer.playback

import com.lalilu.lmedia.entity.LItem

/**
 * Playback实际状态
 */
sealed class PlaybackState {
    /**
     * 闲置状态
     */
    data object Idle : PlaybackState()

    /**
     * 加载中状态
     *
     * @property item 当前正在加载的元素
     */
    data class Loading(val item: LItem) : PlaybackState()

    /**
     * 播放中状态
     *
     * @property item 当前正在播放的元素
     */
    data class Playing(val item: LItem) : PlaybackState()

    /**
     * 暂停状态
     *
     * @property item 当前正在暂停的元素
     */
    data class Paused(val item: LItem) : PlaybackState()

    /**
     * 错误状态
     *
     * @property error 错误信息
     */
    data class Error(val error: Throwable) : PlaybackState()

    /**
     * 停止状态
     */
    data object Stopped : PlaybackState()
}

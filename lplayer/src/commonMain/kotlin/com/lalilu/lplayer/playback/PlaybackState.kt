package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio

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
     * @property item 当前正在加载的音频
     */
    data class Loading(val item: LAudio) : PlaybackState()

    /**
     * 播放中状态
     *
     * @property item 当前正在播放的音频
     */
    data class Playing(val item: LAudio) : PlaybackState()

    /**
     * 暂停状态
     *
     * @property item 当前正在暂停的音频
     */
    data class Paused(val item: LAudio) : PlaybackState()

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

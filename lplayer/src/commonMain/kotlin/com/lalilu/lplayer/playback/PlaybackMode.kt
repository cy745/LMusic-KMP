package com.lalilu.lplayer.playback

/**
 * 播放模式枚举
 */
enum class PlaybackMode {
    /**
     * 顺序播放（播放到列表末尾即停止）
     */
    SEQUENTIAL,

    /**
     * 列表循环
     */
    LOOP,

    /**
     * 单曲循环（同[SEQUENTIAL]）
     */
    SINGLE_LOOP,

    /**
     * 随机播放
     */
    SHUFFLE
}
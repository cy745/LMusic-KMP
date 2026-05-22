package com.lalilu.lplayer.playback

/**
 * 表示播放队列更新的原因。
 */
sealed interface QueueUpdateReason {

    /**
     * 未知原因导致的队列更新。
     */
    data object Unknown : QueueUpdateReason

    /**
     * 内部逻辑触发的队列更新（如播放完成自动下一首、 shuffle 重排等）。
     */
    data object Inner : QueueUpdateReason

    /**
     * 同步操作触发的队列更新（如多端同步、数据刷新等）。
     */
    data object Sync : QueueUpdateReason
}
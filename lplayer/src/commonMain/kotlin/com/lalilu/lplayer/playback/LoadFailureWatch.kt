package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.MediaKey

/**
 * 「加载失败记账 + 播放真实推进后清除」的判定状态机。
 *
 * 平台层负责轮询自己的引擎状态与位置，并把结果交给这里判定：
 * - [onStateError] 返回 true 表示这条错误需要写一次失败记录（同一条错误只记一次）；
 * - [onPositionAdvanced] 返回 true 表示应当清除既有失败记录。
 *
 * [onPositionAdvanced] 在加载后**第一次**真实推进就返回 true，即使这条失败记录是更早的播放段落
 * （或上一次会话）留下的——否则"记录过一次失败"的歌曲在恢复播放成功后永远清不掉。
 * 清除后不再重复清除；同一首歌再次报错会重新武装。
 */
internal class LoadFailureWatch {
    private var lastError: String? = null
    private var needsClear = true

    /** 引擎当前错误（null 表示已恢复正常）。 */
    fun onStateError(message: String?): Boolean {
        if (message == null) {
            lastError = null
            return false
        }
        if (message == lastError) return false
        lastError = message
        needsClear = true
        return true
    }

    /** 观察到正在播放且位置比上一次采样前进。 */
    fun onPositionAdvanced(): Boolean {
        if (!needsClear) return false
        needsClear = false
        return true
    }
}

/**
 * 播放中途失败的自动导航判定。必须同时满足：
 * - 用户确实要求过播放这首歌——暂停/停止之后才出现的错误不算，否则会在用户想停下来看情况时把歌跳走；
 * - 失败项仍是当前加载项与队列当前项——迟到错误、用户已经换歌都不接管；
 * - 位置在两次采样之间没有前进——确认真的停住了，而不是加载慢或缓冲。
 */
internal fun shouldNavigateAfterStalledFailure(
    failedKey: MediaKey,
    currentItemKey: MediaKey?,
    loadedKey: MediaKey?,
    playRequestedKey: MediaKey?,
    positionAdvanced: Boolean,
): Boolean = failedKey == currentItemKey &&
    failedKey == loadedKey &&
    failedKey == playRequestedKey &&
    !positionAdvanced

/**
 * 播放中途失败后继续找下一首的**方向**：跟随「加载当前项时所用的那次导航方向」。
 *
 * Android 的 `onPlayerError` 沿用最近一次导航开启的 traversal 方向，所以按"上一首"进入的歌曲
 * 中途失败时同样向后继续。iOS 没有 traversal，改为把加载方向随已加载项记下来，语义与 Android 对齐。
 *
 * 三个守卫在**导航真正发起时**再复查一遍：失败项仍由同一个引擎持有、仍是被加载项、仍是队列当前项。
 * 任一不成立返回 null，不接管这次错误——迟到的错误或用户已经换歌时不能替用户做决定。
 */
internal fun stalledFailureNavigationDirection(
    engineStillActive: Boolean,
    loadedKeyStillMatches: Boolean,
    queueCurrentStillMatches: Boolean,
    loadDirection: PlaybackDirection,
): PlaybackDirection? = loadDirection.takeIf {
    engineStillActive && loadedKeyStillMatches && queueCurrentStillMatches
}

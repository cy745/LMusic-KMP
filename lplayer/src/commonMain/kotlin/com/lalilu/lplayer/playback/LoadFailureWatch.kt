package com.lalilu.lplayer.playback

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

package com.lalilu.lmedia.domain.debug

/**
 * 调试开关：只由 adb 调试通道（`DebugCommandReceiver`）改写，用来**按需构造要复现的状态**，
 * 免得每次都靠人手动操作 + 碰运气。
 *
 * 放在 domain 是因为两条链都要读它：封面解析在 `lmedia-coil`，播放/时长在 `lplayer`。
 *
 * 生产行为：默认全关，且 [enabled] 只在可调试构建里由调试通道打开；
 * 所有埋点日志都以 [enabled] 为前提，所以正式包不会多出任何日志或分支开销。
 */
object DebugSwitches {
    /** 调试通道是否已启用（可调试构建 + 收到过任意调试命令）。关着的时候埋点完全静默。 */
    var enabled: Boolean = false

    /** 强制封面解析失败：复现「取不到图 → 上一张被清空 → 露出底色」那条路径。 */
    var fakeCoverFail: Boolean = false

    /** 封面解析前的人工延迟（毫秒）：把「还没到」的窗口放大到肉眼/录屏能稳定抓住。 */
    var fakeCoverDelayMs: Long = 0L

    /** 强制时长按「未知」上报：复现切歌时进度条塌成空轨道、总时长显示 00:00。 */
    var fakeDurationUnknown: Boolean = false

    fun reset() {
        fakeCoverFail = false
        fakeCoverDelayMs = 0L
        fakeDurationUnknown = false
    }

    fun describe(): String = buildString {
        append("enabled=").append(enabled)
        append(" coverFail=").append(fakeCoverFail)
        append(" coverDelayMs=").append(fakeCoverDelayMs)
        append(" durationUnknown=").append(fakeDurationUnknown)
    }
}

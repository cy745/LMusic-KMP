package com.lalilu.lmusic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import co.touchlab.kermit.Logger
import com.lalilu.lmedia.domain.debug.DebugSwitches
import com.lalilu.lplayer.LPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val logger = Logger.withTag("DebugCmd")

/**
 * adb 调试通道 —— 把「构造要复现的状态」从"人手动点 + 碰运气"变成一条命令。
 *
 * 为什么需要它：像"切歌瞬间封面闪一下底色"这种问题，窗口只有 ~100ms，
 * 靠人手点出来再复现既慢又不稳定；而"这首歌还没提取过封面"这种前提更是没法手工保证。
 * 有了这个通道，脚本可以先 `cover_clear` 把某首的缓存清掉、再 `index` 切过去，
 * 状态就确定可复现了。
 *
 * 用法（应用需已在前台运行）：
 * ```
 * adb shell am broadcast -a com.lalilu.lmusic.DEBUG --es cmd next
 * adb shell am broadcast -a com.lalilu.lmusic.DEBUG --es cmd index --ei index 3
 * adb shell am broadcast -a com.lalilu.lmusic.DEBUG --es cmd cover_fail --ez on true
 * adb shell am broadcast -a com.lalilu.lmusic.DEBUG --es cmd cover_delay --el ms 1500
 * adb shell am broadcast -a com.lalilu.lmusic.DEBUG --es cmd duration_unknown --ez on true
 * adb shell am broadcast -a com.lalilu.lmusic.DEBUG --es cmd state
 * adb shell am broadcast -a com.lalilu.lmusic.DEBUG --es cmd reset
 * ```
 *
 * 安全：只接受**可调试构建**（[ApplicationInfo.FLAG_DEBUGGABLE]）的调用，正式包直接忽略。
 */
class DebugCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        val i = intent ?: return
        if (!ctx.isDebuggable()) return

        val cmd = i.getStringExtra(EXTRA_CMD)?.trim()?.lowercase().orEmpty()
        if (cmd.isEmpty()) {
            logger.w { "DebugCmd: 缺少 --es $EXTRA_CMD" }
            return
        }
        DebugSwitches.enabled = true

        when (cmd) {
            "play" -> playback { it.play() }
            "pause" -> playback { it.pause() }
            "toggle" -> playback { it.togglePlayPause() }
            "next" -> playback { it.skipToNext() }
            "prev" -> playback { it.skipToPrevious() }
            "seek" -> {
                val ms = i.getLongExtra(EXTRA_MS, 0L)
                playback { it.seekTo(ms) }
                logger.i { "DebugCmd: seek -> ${ms}ms" }
            }
            "index" -> {
                val index = i.getIntExtra(EXTRA_INDEX, 0)
                playback { it.skipTo(index = index, start = true) }
                logger.i { "DebugCmd: skipTo index=$index" }
            }
            "cover_fail" -> {
                DebugSwitches.fakeCoverFail = i.boolExtra(true)
                logger.i { "DebugCmd: fakeCoverFail=${DebugSwitches.fakeCoverFail}" }
            }
            "cover_delay" -> {
                DebugSwitches.fakeCoverDelayMs = i.getLongExtra(EXTRA_MS, 0L)
                logger.i { "DebugCmd: fakeCoverDelayMs=${DebugSwitches.fakeCoverDelayMs}" }
            }
            "duration_unknown" -> {
                DebugSwitches.fakeDurationUnknown = i.boolExtra(true)
                logger.i { "DebugCmd: fakeDurationUnknown=${DebugSwitches.fakeDurationUnknown}" }
            }
            "reset" -> {
                DebugSwitches.reset()
                logger.i { "DebugCmd: 开关已复位" }
            }
            "state" -> {
                val p = LPlayer.instance
                logger.i {
                    "DebugCmd: state switches=[${DebugSwitches.describe()}] " +
                        "isPlaying=${p.isPlaying.value} durationMs=${p.currentDuration.value} " +
                        "positionMs=${p.currentPosition()} " +
                        "bufferedMs=${p.currentBufferedPosition.value}"
                }
            }
            else -> logger.w { "DebugCmd: 未知命令 '$cmd'" }
        }
    }

    private fun Intent.boolExtra(default: Boolean): Boolean =
        if (hasExtra(EXTRA_ON)) getBooleanExtra(EXTRA_ON, default) else default

    private fun playback(block: suspend (com.lalilu.lplayer.playback.Playback) -> Unit) {
        scope.launch {
            runCatching { block(LPlayer.instance) }
                .onFailure { logger.e(it) { "DebugCmd: 播放动作失败" } }
        }
    }

    companion object {
        const val ACTION = "com.lalilu.lmusic.DEBUG"
        const val EXTRA_CMD = "cmd"
        const val EXTRA_ON = "on"
        const val EXTRA_MS = "ms"
        const val EXTRA_INDEX = "index"

        private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        fun isDebuggable(context: Context): Boolean =
            (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}

private fun Context.isDebuggable(): Boolean = DebugCommandReceiver.isDebuggable(this)

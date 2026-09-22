package com.lalilu.lmusic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import co.touchlab.kermit.Logger
import com.lalilu.lmedia.domain.debug.DebugSwitches
import com.lalilu.lmedia.domain.source.MediaCacheDebugControl
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.source.observeBufferProgress
import com.lalilu.lplayer.LPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

private val logger = Logger.withTag("DebugCmd")

/**
 * adb 调试通道 —— 把「构造要复现的状态」从"人手动点 + 碰运气"变成一条命令。
 *
 * 为什么需要它：像"切歌瞬间封面闪一下底色"这种问题，窗口只有 ~100ms，
 * 靠人手点出来再复现既慢又不稳定；而"这首歌还没提取过封面"这种前提更是没法手工保证。
 * 有了这个通道，脚本可以先 `clear_cache` 把某首的缓存清掉、再 `index` 切过去，
 * 状态就确定可复现了。
 *
 * 用法（应用需已在前台运行）：
 * ```
 * adb shell am broadcast -a com.lalilu.lmusic.DEBUG --es cmd next
 * adb shell am broadcast -a com.lalilu.lmusic.DEBUG --es cmd index --ei index 3
 * adb shell am broadcast -a com.lalilu.lmusic.DEBUG --es cmd cover_fail --ez on true
 * adb shell am broadcast -a com.lalilu.lmusic.DEBUG --es cmd cover_delay --el ms 1500
 * adb shell am broadcast -a com.lalilu.lmusic.DEBUG --es cmd duration_unknown --ez on true
 * adb shell am broadcast -a com.lalilu.lmusic.DEBUG --es cmd clear_cache --ei index 4
 * adb shell am broadcast -a com.lalilu.lmusic.DEBUG --es cmd clear_cache --ez all true
 * adb shell am broadcast -a com.lalilu.lmusic.DEBUG --es cmd queue
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
            "clear_cache" -> clearCache(i)
            "queue" -> {
                val state = LPlayer.instance.queue.stateSnapshot()
                logger.i { "DebugCmd: queue size=${state.list.size} index=${state.index}" }
                state.list.forEachIndexed { idx, audio ->
                    logger.i {
                        "DebugCmd: queue[$idx] source=${audio.mediaSourceName} " +
                            "id=${audio.id} title='${audio.title}'"
                    }
                }
            }
            "reset" -> {
                DebugSwitches.reset()
                logger.i { "DebugCmd: 开关已复位" }
            }
            "state" -> {
                val p = LPlayer.instance
                val queue = p.queue.stateSnapshot()
                val item = queue.currentItem()
                logger.i {
                    "DebugCmd: state switches=[${DebugSwitches.describe()}] " +
                        "isPlaying=${p.isPlaying.value} durationMs=${p.currentDuration.value} " +
                        "positionMs=${p.currentPosition()} " +
                        "bufferedMs=${p.currentBufferedPosition.value} " +
                        "queueIndex=${queue.index}/${queue.list.size} " +
                        "item=${item?.id} source=${item?.mediaSourceName}"
                }
                logBufferRanges(item)
            }
            else -> logger.w { "DebugCmd: 未知命令 '$cmd'" }
        }
    }

    /**
     * 单独一行打印当前这首**数据源上报的缓冲区间**。
     *
     * 为什么和 `state` 分开：`state` 里的 bufferedMs 是播放器自己的缓冲位置，而渐进式 HTTP 下
     * 它常常是 0——真正能看出"还有多久能听"的是数据源的缓存覆盖率（字节比例），要挂起去取，
     * 所以另起一行、晚一点到，而不是把 state 变成挂起调用。
     */
    private fun logBufferRanges(item: com.lalilu.lmedia.domain.model.LAudio?) {
        if (item == null) return
        scope.launch {
            runCatching {
                KoinPlatform.getKoin().get<PlatformMediaSource>()
                    .observeBufferProgress(item)
                    .first()
            }.onSuccess { ranges ->
                logger.i {
                    "DebugCmd: buffer audio=${item.id} ranges=" +
                        ranges.joinToString { "%.3f~%.3f".format(it.startFraction, it.endFraction) }
                }
            }.onFailure { logger.w(it) { "DebugCmd: buffer 读取失败" } }
        }
    }

    private fun Intent.boolExtra(default: Boolean): Boolean =
        if (hasExtra(EXTRA_ON)) getBooleanExtra(EXTRA_ON, default) else default

    /**
     * 清掉某首歌（或整个来源）的本地缓存，把它打回"没加载过"的样子。
     *
     * 三种指定方式，必须显式给出一种：`--es id <audioId>`、`--ei index <队列下标>`、
     * `--ez all true`。什么都不给时**不清任何东西**——"顺手清全部"会让脚本的一次笔误把
     * 整个媒体库的缓存抹掉，代价太大。
     */
    private fun clearCache(i: Intent) {
        val id = i.getStringExtra(EXTRA_ID)?.takeIf { it.isNotBlank() }
        val index = if (i.hasExtra(EXTRA_INDEX)) i.getIntExtra(EXTRA_INDEX, -1) else -1
        val all = i.boolExtra(default = false)

        if (id == null && index < 0 && !all) {
            logger.w { "DebugCmd: clear_cache 需要 --es id / --ei index / --ez all true 之一" }
            return
        }

        scope.launch {
            runCatching {
                val audioId = when {
                    all -> null
                    id != null -> id
                    else -> LPlayer.instance.queue.stateSnapshot().list.getOrNull(index)?.id
                }
                if (!all && audioId == null) {
                    logger.w { "DebugCmd: clear_cache 队列里没有 index=$index" }
                    return@runCatching
                }

                val sources = KoinPlatform.getKoin().get<PlatformMediaSource>().sources
                sources.forEach { source ->
                    val control = source as? MediaCacheDebugControl ?: return@forEach
                    runCatching { control.clearLocalCache(audioId) }
                        .onSuccess {
                            logger.i { "DebugCmd: clear_cache source=${source.name} target=${audioId ?: "ALL"}" }
                        }
                        .onFailure { logger.e(it) { "DebugCmd: clear_cache source=${source.name} 失败" } }
                }
            }.onFailure { logger.e(it) { "DebugCmd: clear_cache 失败" } }
        }
    }

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
        const val EXTRA_ID = "id"

        private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        fun isDebuggable(context: Context): Boolean =
            (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}

private fun Context.isDebuggable(): Boolean = DebugCommandReceiver.isDebuggable(this)

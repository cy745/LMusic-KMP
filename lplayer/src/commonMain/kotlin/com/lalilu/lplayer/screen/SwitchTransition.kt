package com.lalilu.lplayer.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * 切歌时进度条的过渡状态（方案 A + B 的组合）。
 *
 * 为什么需要它：时长与缓冲区间**都不是瞬间就有的**（网络媒体源尤其如此，实测切歌后
 * 要过几百毫秒到两秒才知道时长）。现在这两个值在切歌时会一起塌成
 * 「填充 0 + 缓冲带空 + 总时长 00:00」，然后等数据到了再硬切回来——看起来就是"进度消失了"。
 *
 * 过渡策略：
 * - **A（延迟接管）**：切歌后先**沿用上一首的显示值**，不做清空；
 * - **B（不确定态）**：超过 [staleMs] 还没拿到新时长，就切到显式占位
 *   （总时长显示 `--:--`、不画填充），而不是继续显示一个假的数字；
 * - 新时长一旦到达，数字换真值、填充从 0 重新推进。
 *
 * 只处理"显示层"的过渡，不改变任何播放状态。
 */
@Stable
internal class SwitchTransition {
    /** 给进度条用的总时长（切歌瞬间是上一首的值，占位态是 0）。 */
    var displayDuration by mutableLongStateOf(0L)
        private set

    /** true = 总时长未知，进度条应显示 `--:--` 且不画填充。 */
    var unknown by mutableStateOf(false)
        private set

    /** 最近一次真的拿到的时长；切歌时用它"接管"显示。 */
    private var held by mutableLongStateOf(0L)

    internal fun onDurationArrived(duration: Long) {
        held = duration
        displayDuration = duration
        unknown = false
    }

    internal fun onSwitch(previousHeld: Long) {
        // A：先沿用上一首的状态，不清空
        displayDuration = previousHeld
        unknown = false
    }

    /** 超过阈值仍然没拿到新时长 → B：切占位 */
    internal fun onStaleTimeout(previousHeld: Long) {
        if (held != previousHeld) return      // 期间已经拿到了，别把真值盖掉
        displayDuration = 0L
        unknown = true
    }

    internal val heldDuration: Long get() = held
}

/**
 * @param playbackKey 当前曲目身份（换歌时变化）
 * @param duration 播放器上报的时长，0 表示还不知道
 * @param staleMs 沿用上一首显示的时长上限；超过就切占位
 */
@Composable
internal fun rememberSwitchTransition(
    playbackKey: Any?,
    duration: Long,
    staleMs: Long = 350L,
): SwitchTransition {
    val state = remember { SwitchTransition() }

    // 新时长到达：立刻换真值（这块不管是不是刚切歌）
    androidx.compose.runtime.LaunchedEffect(duration) {
        if (duration > 0L) state.onDurationArrived(duration)
    }

    androidx.compose.runtime.LaunchedEffect(playbackKey) {
        if (playbackKey == null) return@LaunchedEffect
        val previousHeld = state.heldDuration
        state.onSwitch(previousHeld)
        delay(staleMs)
        state.onStaleTimeout(previousHeld)
    }

    return state
}

package com.lalilu.lplayer.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lplayer.LPlayer
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.exp
import kotlin.math.roundToLong

/**
 * 弹簧的固有频率（rad/s）。
 *
 * sqrt(Spring.StiffnessLow) ≈ 14，与旧版 `spring(stiffness = Spring.StiffnessLow)` 的手感一致：
 * 从静止出发约 0.45s 收敛到位，且第一帧只移动约 2% 的距离——是"滑过去"而不是"跳一下再滑"。
 */
private const val SpringOmega = 14f

/** 残差收敛到该值以内且速度足够小时直接归零。 */
private const val ResidualSnapThresholdMs = 0.5f
private const val ResidualSnapVelocityMs = 5f

/** 比例收敛到该值以内且速度足够小时直接对齐。 */
private const val ProgressSnapThreshold = 0.0005f
private const val ProgressSnapVelocity = 0.01f

/**
 * 引擎位置相对上一帧的增量超过该值时视为一次不连续（seek / 切歌 / 恢复）。
 * 只用来判断"这次跳变要不要吸收"，不参与任何动画风格决策。
 */
private const val DiscontinuityToleranceMs = 150f

/** 引擎位置与 seek 目标相差在该范围内即视为落地。 */
private const val LandingToleranceMs = 250L

/** 等待引擎落地的最长时间，超时后平滑回到引擎的真实位置。 */
private const val CommitTimeoutMs = 600L

/** 两帧间隔超过该值（后台 / 长暂停）时认为连续性已无意义，直接对齐真实位置。 */
private const val MaxContinuityGapMs = 500L

/** 暂停时读取引擎位置的间隔；过渡动画不受它影响，仍然逐帧推进。 */
private const val PausedPollIntervalMs = 200L

/** 首帧拿不到真实间隔时的兜底值。 */
private const val FirstFrameFallbackMs = 16L

/**
 * 临界阻尼弹簧跟随器。
 *
 * 用它而不是一阶低通：低通的瞬时速度在起点最大，会把一次大跳变的第一帧就吃掉四分之一，
 * 看起来仍是"跳变"；临界阻尼弹簧从零速度起步，位移是连续的。
 */
private class SpringFollower(
    initialValue: Float,
    private val omega: Float,
    private val snapThreshold: Float,
    private val snapVelocity: Float,
) {
    var value = initialValue
        private set

    private var velocity = 0f

    /** 直接对齐到目标并清零速度。用于手势接管、提交等"必须精确"的时刻。 */
    fun snapTo(target: Float) {
        value = target
        velocity = 0f
    }

    /**
     * 吸收一次外部跳变：只移动位置，不注入速度。
     *
     * 这样跳变后的收敛仍然从静止开始，不会在第一帧产生突兀的位移。
     */
    fun offsetBy(delta: Float) {
        value += delta
        velocity = 0f
    }

    fun step(target: Float, dtSeconds: Float) {
        // 临界阻尼的解析解，避免显式积分在大 dt 下的误差被放大成"看起来是欠阻尼"的抖动。
        val offset = value - target
        val slope = velocity + omega * offset
        val decay = exp(-omega * dtSeconds)

        value = target + (offset + slope * dtSeconds) * decay
        velocity = (slope - omega * (offset + slope * dtSeconds)) * decay

        if ((target - value).absoluteValue < snapThreshold &&
            velocity.absoluteValue < snapVelocity
        ) {
            snapTo(target)
        }
    }
}

/**
 * 播放页唯一的时间轴。
 *
 * 它同时拥有"现在是什么时间"、"这首歌曲有多长"和"缓冲到哪里"，三者都绑定在同一个曲目上，
 * 因此不会出现新歌的位置配旧歌的时长这类中间态。时长由 Playback 层的稳定时长提供，
 * 这里只负责在拖动时冻结它，并把比例变化抹平。
 *
 * 三条规则：
 * 1. 稳态时 [positionMs] 与引擎位置完全一致（残差恒为 0），不引入任何延迟；
 * 2. 引擎位置出现非预期跳变（seek / 切歌 / 恢复）时，把跳变量吸收进残差，
 *    显示值保持连续，再以临界阻尼弹簧平滑过渡到真实位置；
 * 3. 用户提交 seek 后进入提交态，等待引擎落地期间显示值只前进、不回退。
 *
 * [progress] 在比例空间平滑跟随，保留拖动与歌词联动手感。
 * [bufferedProgress] 直接呈现真实可播放范围，不对缓存事实做插值或模拟下载。
 */
@Stable
internal class PlaybackTimelineState(
    initialPositionMs: Long = 0L,
    initialPlaybackKey: Any? = null,
) {
    /** 当前用于渲染与歌词的时间（毫秒）。 */
    var positionMs by mutableLongStateOf(initialPositionMs)
        private set

    /** 当前曲目的稳定时长（毫秒），来自 Playback 层。0 表示未知，此时不允许拖动。 */
    var durationMs by mutableLongStateOf(0L)
        private set

    /** 当前播放进度比例（0..1），已做平滑。 */
    var progress by mutableFloatStateOf(0f)
        private set

    /** 当前实际缓冲范围的末端（0..1）；直接显示事实，不模拟加载动画。 */
    var bufferedProgress by mutableFloatStateOf(0f)
        private set

    /** 用户是否正在拖动进度条。 */
    var isDragging by mutableStateOf(false)
        private set

    /**
     * 当前时间轴归属的曲目身份。
     *
     * 必须与歌词内容的 key（`LAudio.id`）一致：歌词页用 `content.key == playbackKey`
     * 判断"这个时间是否已经属于当前页"，不一致就会一直停在未同步状态而不跟随滚动。
     */
    var playbackKey: Any? by mutableStateOf(initialPlaybackKey)
        private set

    val canSeek: Boolean get() = durationMs > 0L

    private var dragPlaybackKey: Any? = null
    private var samplePositionMs = initialPositionMs
    private var bufferedPositionMs = 0L
    private val residual = SpringFollower(
        initialValue = 0f,
        omega = SpringOmega,
        snapThreshold = ResidualSnapThresholdMs,
        snapVelocity = ResidualSnapVelocityMs,
    )
    private val progressFollower = SpringFollower(
        initialValue = 0f,
        omega = SpringOmega,
        snapThreshold = ProgressSnapThreshold,
        snapVelocity = ProgressSnapVelocity,
    )
    private var commit: Commit? = null
    private var clockMs = 0L

    private data class Commit(
        val targetMs: Long,
        val fromMs: Long,
        val startedAtMs: Long,
    )

    /**
     * 曲目身份变化时调用。
     *
     * 位置不在这里重置——旧显示值会由下一帧的不连续吸收保留，再平滑滑向新曲目的位置；
     * 时长与缓冲进度则跟着曲目一起归零，等下一帧的采样送来新曲目的真实值。
     */
    fun onPlaybackKeyChanged(key: Any?) {
        if (key == playbackKey) return

        playbackKey = key
        dragPlaybackKey = null
        isDragging = false
        commit = null
        bufferedPositionMs = 0L
        bufferedProgress = 0f
    }

    fun onDragStart(positionMs: Long) {
        if (!canSeek) return
        // 用户重新抓住进度条：上一次提交即使还没落地，也立刻交回给手指决定。
        commit = null
        isDragging = true
        dragPlaybackKey = playbackKey
        setDisplayPosition(positionMs.coerceIn(0L, durationMs))
        progress = ratioOf(this.positionMs)
    }

    fun onDrag(positionMs: Long) {
        if (!isDragging) return
        setDisplayPosition(positionMs.coerceIn(0L, durationMs))
        progress = ratioOf(this.positionMs)
    }

    fun onDragCancel() {
        if (!isDragging) return
        isDragging = false
        // 残差与速度都从当前显示值重新开始，随后的采样会把它平滑衰减回真实播放位置。
        residual.snapTo((positionMs - samplePositionMs).toFloat())
    }

    fun onSeek(positionMs: Long) {
        if (!isDragging) return
        isDragging = false
        // 拖动期间切了歌：放弃这次提交，交给曲目切换的过渡处理。
        if (dragPlaybackKey != playbackKey) return

        val target = positionMs.coerceIn(0L, durationMs)
        commit = Commit(targetMs = target, fromMs = target, startedAtMs = clockMs)
        setDisplayPosition(target)
        progress = ratioOf(target)
    }

    /**
     * 由播放器时钟每帧推入一次。
     *
     * 暂停时引擎位置可能沿用上一次读取的缓存值，但 [dtMs] 仍是真实帧间隔，
     * 因此弹簧与比例始终按帧推进。
     */
    fun accept(
        enginePositionMs: Long,
        engineDurationMs: Long,
        bufferedPositionMs: Long,
        isPlaying: Boolean,
        dtMs: Long,
    ) {
        val elapsedMs = dtMs.coerceIn(0L, MaxContinuityGapMs * 4)
        clockMs += elapsedMs

        // 拖动期间冻结时长域：手指下的比例换算不能因为时长修正而改变。
        this.bufferedPositionMs = bufferedPositionMs
        if (!isDragging) applyDuration(engineDurationMs)

        val previous = samplePositionMs
        samplePositionMs = enginePositionMs

        if (elapsedMs > MaxContinuityGapMs) {
            // 后台或长暂停之后，连续性已经没有意义，直接对齐真实位置。
            commit = null
            setDisplayPosition(enginePositionMs)
            progressFollower.snapTo(ratioOf(positionMs))
            progress = progressFollower.value
            bufferedProgress = ratioOf(bufferedPositionMs)
            return
        }

        val dtSeconds = elapsedMs / 1000f
        val expected = if (isPlaying) elapsedMs.toFloat() else 0f
        val jump = (enginePositionMs - previous).toFloat()

        val pending = commit
        if (pending != null) {
            val landed = (enginePositionMs - pending.targetMs).absoluteValue <= LandingToleranceMs
            if (landed) {
                commit = null
                setDisplayPosition(enginePositionMs)
                refreshProgress(dtSeconds)
                return
            }

            if (clockMs - pending.startedAtMs > CommitTimeoutMs) {
                // 超时仍未落地：放弃等待，落到下面的常规处理里平滑回到真实位置。
                commit = null
            } else {
                // 等待引擎落地期间，显示值从目标位置按播放速率继续前进，绝不回退。
                val elapsedSinceCommit = clockMs - pending.startedAtMs
                val advanced = if (isPlaying) elapsedSinceCommit else 0L
                setDisplayPosition(pending.fromMs + advanced)
                refreshProgress(dtSeconds)
                return
            }
        }

        if (isDragging) {
            setDisplayPosition(positionMs)
            refreshProgress(dtSeconds)
            return
        }

        if ((jump - expected).absoluteValue > DiscontinuityToleranceMs) {
            // 吸收这次跳变，保证显示值连续；随后由弹簧平滑过渡到真实位置。
            residual.offsetBy(expected - jump)
        }

        residual.step(target = 0f, dtSeconds = dtSeconds)
        positionMs = (enginePositionMs + residual.value).roundToLong()
        refreshProgress(dtSeconds)
    }

    private fun setDisplayPosition(positionMs: Long) {
        this.positionMs = positionMs
        residual.snapTo((positionMs - samplePositionMs).toFloat())
    }

    private fun applyDuration(valueMs: Long) {
        val wasUnknown = durationMs <= 0L
        durationMs = valueMs.coerceAtLeast(0L)
        // 从"未知"变为"已知"时没有可延续的比例，直接对齐，避免冷启动时进度条自己涨一段。
        if (wasUnknown && durationMs > 0L) {
            progressFollower.snapTo(ratioOf(positionMs))
            progress = progressFollower.value
            bufferedProgress = ratioOf(bufferedPositionMs)
        }
    }

    private fun refreshProgress(dtSeconds: Float) {
        val target = ratioOf(positionMs)
        if (isDragging) {
            progressFollower.snapTo(target)
        } else {
            progressFollower.step(target, dtSeconds)
        }
        progress = progressFollower.value

        bufferedProgress = ratioOf(bufferedPositionMs)
    }

    private fun ratioOf(positionMs: Long): Float =
        if (durationMs <= 0L) 0f
        else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/**
 * 时间轴使用的曲目身份：必须是歌词内容的 key。
 *
 * 单独抽出来是为了让这条契约可被测试钉住——曾经因为改用 source-qualified 的
 * `LAudio.mediaKey` 而导致歌词永远不跟随滚动。
 */
internal fun timelineKeyOf(item: LAudio?): Any? = item?.id

/**
 * 播放页时间轴的常规入口：曲目身份从 [item] 推导，时间、时长与缓冲都直接读 Playback 层。
 */
@Composable
internal fun rememberPlaybackTimelineState(
    isPlaying: Boolean,
    item: LAudio?,
    readPosition: () -> Long = { LPlayer.instance.currentPosition() },
): PlaybackTimelineState = rememberPlaybackTimelineState(
    isPlaying = isPlaying,
    playbackKey = timelineKeyOf(item),
    readPosition = readPosition,
)

/**
 * 播放页时钟：把引擎的离散采样推入 [PlaybackTimelineState]。
 *
 * 直接推送（而不是经过 snapshotFlow）是刻意的：时间与歌词必须在同一帧内读到同一个值，
 * 任何一次调度转发都会让两者出现一帧的偏差。
 */
@Composable
internal fun rememberPlaybackTimelineState(
    isPlaying: Boolean,
    playbackKey: Any?,
    readPosition: () -> Long,
    readDuration: () -> Long = { LPlayer.instance.stableDuration.value },
    readBufferedPosition: () -> Long = { LPlayer.instance.currentBufferedPosition.value },
): PlaybackTimelineState {
    val scope = rememberCoroutineScope()
    val currentReadPosition by rememberUpdatedState(readPosition)
    val currentReadDuration by rememberUpdatedState(readDuration)
    val currentReadBufferedPosition by rememberUpdatedState(readBufferedPosition)
    val state = remember {
        PlaybackTimelineState(
            initialPositionMs = currentReadPosition(),
            initialPlaybackKey = playbackKey,
        )
    }

    LaunchedEffect(playbackKey) {
        state.onPlaybackKeyChanged(playbackKey)
    }

    LifecycleResumeEffect(isPlaying) {
        val job = scope.launch {
            var lastFrameMs = 0L
            var lastSampleMs = 0L
            var samplePositionMs = currentReadPosition()

            // 动画必须逐帧推进，采样频率则可以独立降低：
            // 暂停时若只在采样时推进一次，回正/切歌的缓动就会退化成 5fps 的阶梯。
            while (isActive) {
                withFrameMillis { frameTimeMs ->
                    val dt = if (lastFrameMs == 0L) FirstFrameFallbackMs
                    else frameTimeMs - lastFrameMs
                    lastFrameMs = frameTimeMs

                    val sampleDue = isPlaying ||
                            frameTimeMs - lastSampleMs >= PausedPollIntervalMs
                    if (sampleDue) {
                        samplePositionMs = currentReadPosition()
                        lastSampleMs = frameTimeMs
                    }

                    state.accept(
                        enginePositionMs = samplePositionMs,
                        engineDurationMs = currentReadDuration(),
                        bufferedPositionMs = currentReadBufferedPosition(),
                        isPlaying = isPlaying,
                        dtMs = dt,
                    )
                }
            }
        }
        onPauseOrDispose { job.cancel() }
    }

    return state
}

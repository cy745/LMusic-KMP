package com.lalilu.lplayer.extensions

import androidx.compose.animation.core.*
import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class VolumeFadeHelper(
    val debug: Boolean = false,
    val onSetVolume: (volume: Float) -> Unit,
    val onGetVolume: (() -> Float)? = null,
    /** 返回当前引擎是否支持音量渐变；false 时 play/pause 跳过动画直接执行 */
    val fadeEnabled: () -> Boolean = { true },
) : CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.io
    private var currentVelocity: Float = 0f
    private var animationJob: Job? = null

    companion object {
        const val UPDATE_DELAY = 50L
    }

    private var volumeOverride = 0f
        get() = onGetVolume?.invoke() ?: field
        set(value) {
            if (field == value) return
            field = value
            launch(Dispatchers.Main) {
                val newVolume = (value / 100f).coerceIn(0f..1f)
                onSetVolume(newVolume)
                if (debug) {
                    Logger.i("onSetVolume: $newVolume")
                }
            }
        }

    fun play(superPlay: () -> Unit = {}) {
        if (!fadeEnabled()) {
            superPlay()
            if (debug) Logger.i("fade disabled, play immediately")
            return
        }
        animationJob?.cancel()
        animationJob = launch(Dispatchers.io) {
            runAnimation(targetValue = 100f)
        }
        superPlay()
        if (debug) {
            Logger.i("superPlay: $volumeOverride")
        }
    }

    fun pause(superPause: suspend () -> Unit = {}) {
        if (!fadeEnabled()) {
            launch { superPause() }
            if (debug) Logger.i("fade disabled, pause immediately")
            return
        }
        animationJob?.cancel()
        animationJob = launch(Dispatchers.io) {
            runAnimation(targetValue = 0f)
            ensureActive()
            withContext(Dispatchers.Main) {

                superPause()
                if (debug) {
                    Logger.i("superPause: $volumeOverride")
                }
            }
        }
    }

    private suspend fun runAnimation(
        targetValue: Float
    ) = withContext(Dispatchers.io) {
        if (targetValue == volumeOverride) {
            return@withContext
        }

        val breakTimeFunc: () -> Boolean = if (targetValue > volumeOverride) {
            { volumeOverride >= targetValue }
        } else {
            { volumeOverride <= targetValue }
        }

        val animation = TargetBasedAnimation(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow,
                visibilityThreshold = 0.001f
            ),
            typeConverter = Float.VectorConverter,
            initialValue = volumeOverride,
            targetValue = targetValue,
            initialVelocity = currentVelocity,
        )

        var time = 0L
        while (isActive) {
            val now = time * 1000L * 1000L
            volumeOverride = animation.getValueFromNanos(now)
            currentVelocity = animation.getVelocityFromNanos(now)

            if (breakTimeFunc()) {
                volumeOverride = targetValue
                currentVelocity = 0f
                break
            }

            time += UPDATE_DELAY
            delay(UPDATE_DELAY)
        }
    }

    fun updateVolume(volume: Float) {
        volumeOverride = volume
    }
}
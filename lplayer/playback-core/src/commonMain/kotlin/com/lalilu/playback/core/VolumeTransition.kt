package com.lalilu.playback.core

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.time.TimeSource

/** Distinct control purposes, shared across runtimes; natural boundaries bypass gain animation. */
enum class VolumeTransition(val durationMs: Long) {
    UserControl(220),
    TrackChange(60),
    Immediate(0),
}

private val gainClockOrigin = TimeSource.Monotonic.markNow()

/** Latest-command cancellation is owned by the runtime. Real elapsed time avoids stretching under load. */
suspend fun rampVolume(
    initial: Float,
    target: Float,
    transition: VolumeTransition,
    nowMillis: () -> Long = { gainClockOrigin.elapsedNow().inWholeMilliseconds },
    applyGain: (Float) -> Unit,
) {
    val from = initial.coerceIn(0f, 1f)
    val to = target.coerceIn(0f, 1f)
    val start = nowMillis()
    while (true) {
        currentCoroutineContext().ensureActive()
        val elapsed = (nowMillis() - start).coerceAtLeast(0)
        if (from == to || elapsed >= transition.durationMs) { applyGain(to); return }
        val fraction = elapsed.toFloat() / transition.durationMs
        val eased = fraction * fraction * (3f - 2f * fraction)
        applyGain(from + (to - from) * eased)
        delay(minOf(12L, transition.durationMs - elapsed))
    }
}

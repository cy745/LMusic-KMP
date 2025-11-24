package com.lalilu.lplayer.extensions

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive

@OptIn(UnstableApi::class)
class FadeTransitionAudioSink(
    sink: AudioSink,
    val scope: CoroutineScope,
) : ForwardingAudioSink(sink) {
    private var volumeOverride = 0f
        set(value) {
            field = value
            super.setVolume((value / 100f).coerceIn(0f..1f))
        }

    private val animator = Animatable(initialValue = 0f, visibilityThreshold = 0.001f)
    private var animationJob: Job? = null

    override fun setVolume(volume: Float) {
        volumeOverride = volume
    }

    override fun play() {
        animationJob?.cancel()
        animationJob = scope.launch(Dispatchers.Main) {
            animator.animateTo(
                targetValue = 100f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                block = { volumeOverride = value }
            )
        }
        super.play()
    }

    override fun pause() {
        animationJob?.cancel()
        animationJob = scope.launch(Dispatchers.Main) {
            animator.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                block = { volumeOverride = value }
            )
            ensureActive()
            super.pause()
        }
    }
}

@OptIn(UnstableApi::class)
class FadeTransitionRenderersFactory(
    context: Context,
    val scope: CoroutineScope,
    teeBufferListener: TeeAudioProcessor.AudioBufferSink? = null,
) : DefaultRenderersFactory(context), AudioProcessorChain {

    private val teeAudioProcessor = teeBufferListener
        ?.let { TeeAudioProcessor(it) }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        val defaultAudioSink = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessorChain(this)
            .build()

        return FadeTransitionAudioSink(defaultAudioSink, scope)
    }

    override fun getAudioProcessors(): Array<AudioProcessor> {
        return if (teeAudioProcessor != null) arrayOf(teeAudioProcessor)
        else emptyArray()
    }

    override fun getMediaDuration(playoutDuration: Long): Long = playoutDuration
    override fun getSkippedOutputFrameCount(): Long = 0
    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean = skipSilenceEnabled
    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters =
        playbackParameters
}
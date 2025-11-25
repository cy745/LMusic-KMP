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

@OptIn(UnstableApi::class)
class FadeTransitionAudioSink(sink: AudioSink) : ForwardingAudioSink(sink) {
    private val volumeHelper = VolumeFadeHelper(onSetVolume = { super.setVolume(it) })

    override fun setVolume(volume: Float) = volumeHelper.updateVolume(volume)

    override fun play() = volumeHelper.play { super.play() }

    override fun pause() = volumeHelper.pause { super.pause() }
}

@OptIn(UnstableApi::class)
class FadeTransitionRenderersFactory(
    context: Context,
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

        return FadeTransitionAudioSink(defaultAudioSink)
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
package com.lalilu.lplayer.core

import android.content.Context
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.audio.TeeAudioProcessor

/** Experience output: stable 48 kHz stereo for mono/stereo music, allowing sink reuse across rates.
 * Multichannel layouts retain their channels; direct/Hi-Res output is a separate future capability.
 */
class ContinuousAudioRenderersFactory(
    context: Context,
    private val observer: TeeAudioProcessor.AudioBufferSink? = null,
) : DefaultRenderersFactory(context) {
    init { setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON) }

    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
        val channels = ChannelMixingAudioProcessor().apply {
            putChannelMixingMatrix(ChannelMixingMatrix.createForConstantGain(1,2))
            putChannelMixingMatrix(ChannelMixingMatrix.createForConstantGain(2,2))
            for (count in 3..8) putChannelMixingMatrix(ChannelMixingMatrix(count,count,
                FloatArray(count*count) { if(it/count == it%count) 1f else 0f }))
        }
        val sonic = SonicAudioProcessor().apply { setOutputSampleRateHz(48000) }
        val chain = object : DefaultAudioSink.DefaultAudioProcessorChain(arrayOf(channels),SilenceSkippingAudioProcessor(),sonic) {
            private val tee = observer?.let(::TeeAudioProcessor)
            override fun getAudioProcessors(): Array<AudioProcessor> = super.getAudioProcessors().let { processors ->
                tee?.let { processors + it } ?: processors
            }
        }
        return DefaultAudioSink.Builder(context).setAudioProcessorChain(chain).build()
    }
}

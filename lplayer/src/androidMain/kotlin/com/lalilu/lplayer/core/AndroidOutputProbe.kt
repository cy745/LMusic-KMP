package com.lalilu.lplayer.core

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import com.lalilu.playback.core.*

/** Observe only: advertised profiles never activate direct output or count as bit-perfect evidence. */
class AndroidOutputProbe(context: Context, private val player: ExoPlayer) {
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var report = OutputCapabilities(backend = "Media3 / AudioTrack",
        original = Support.Unsupported,
        originalReason = "当前使用 48 kHz PCM 体验链路，未接入并验证独占或绕过系统 SRC 的输出路径",
        gapless = Support.Supported,
        route = "系统选择；当前实际路由未由 AudioTrack 暴露")
    private var closed = false
    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refreshDevices()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refreshDevices()
    }
    private val listener = object : AnalyticsListener {
        override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, config: AudioSink.AudioTrackConfig) {
            report = report.copy(apiFormat = OutputFormat(config.sampleRate, Integer.bitCount(config.channelConfig),
                when (config.encoding) {
                    C.ENCODING_PCM_16BIT -> "PCM 16 bit"
                    C.ENCODING_PCM_FLOAT -> "PCM float"
                    C.ENCODING_PCM_24BIT -> "PCM 24 bit"
                    C.ENCODING_PCM_32BIT -> "PCM 32 bit"
                    else -> "encoding ${config.encoding}"
                }), apiFormatEvidence = "AudioTrack 初始化回调")
            publish()
        }
        override fun onAudioTrackReleased(eventTime: AnalyticsListener.EventTime, config: AudioSink.AudioTrackConfig) {
            report = report.copy(apiFormat = null, apiFormatEvidence = null)
            publish()
        }
    }
    init {
        player.addAnalyticsListener(listener)
        manager.registerAudioDeviceCallback(devices, Handler(Looper.getMainLooper()))
        refreshDevices()
    }
    private fun publish() { if (!closed) PlaybackOutput.report(report) }
    private fun refreshDevices() {
        if (closed) return
        val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        report = report.copy(availableDevices = outputs.map { "${it.productName} (type ${it.type})" }.distinct(),
            advertisedSampleRates = outputs.flatMap { it.sampleRates.toList() }.distinct().sorted())
        publish()
    }
    fun close() {
        closed = true
        player.removeAnalyticsListener(listener)
        manager.unregisterAudioDeviceCallback(devices)
        PlaybackOutput.report(OutputCapabilities())
    }
}

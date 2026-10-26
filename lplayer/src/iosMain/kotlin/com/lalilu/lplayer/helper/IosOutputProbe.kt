package com.lalilu.lplayer.helper

import com.lalilu.lplayer.core.PlaybackOutput
import com.lalilu.playback.core.*
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.*
import platform.Foundation.*

@OptIn(ExperimentalForeignApi::class)
object IosOutputProbe {
    private var installed = false
    fun start() {
        if (!installed) {
            installed = true
            listOf(AVAudioSessionRouteChangeNotification, AVAudioSessionMediaServicesWereResetNotification).forEach { name ->
                NSNotificationCenter.defaultCenter.addObserverForName(name, AVAudioSession.sharedInstance(),
                    NSOperationQueue.mainQueue) { refresh() }
            }
        }
        refresh()
    }
    private fun refresh() {
        val session = AVAudioSession.sharedInstance()
        val route = session.currentRoute.outputs.filterIsInstance<AVAudioSessionPortDescription>()
            .joinToString { it.portName }
        PlaybackOutput.report(OutputCapabilities(backend = "AVFoundation / MusicKit",
            original = Support.Unsupported,
            originalReason = "AVAudioSession 的采样率不能证明独占或位一致输出，当前未接入可验证的原样链路",
            // AVQueuePlayer stages a successor; physical continuity still depends on media/backend.
            gapless = Support.Unknown, route = route.ifBlank { null },
            apiFormat = session.sampleRate.takeIf { it > 0 }?.let { OutputFormat(it.toInt(), session.outputNumberOfChannels.toInt()) },
            apiFormatEvidence = "AVAudioSession 当前硬件会话配置"))
    }
}

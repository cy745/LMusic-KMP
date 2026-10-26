package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.repository.*
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lplayer.NativeExtractor
import com.lalilu.lplayer.core.*
import com.lalilu.lplayer.menu.MacOSMenu
import com.lalilu.lplayer.notification.MacOSNotification
import com.lalilu.lplayer.player.VLCPlayer
import com.lalilu.lplayer.player.VLCPlayerLoader
import kotlinx.coroutines.*
import org.koin.core.annotation.Single
import uk.co.caprica.vlcj.player.base.MediaPlayer
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.path
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

@Single(binds = [Playback::class])
fun provideVLCPlayback(audioRepository: AudioRepository, history: PlaybackHistory,
    platformMediaSource: PlatformMediaSource, dataTracker: IPlaybackDataTracker,
    playbackFailureRepository: PlaybackFailureRepository): VLCPlayback {
    val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob())
    val content = NativeContentAccess(scope, platformMediaSource, audioRepository,
        HttpClient(OkHttp) { install(HttpTimeout) { connectTimeoutMillis = 15000; socketTimeoutMillis = 15000; requestTimeoutMillis = 30000 } },
        "${FileKit.filesDir.path}/plan-b-catalog", "${FileKit.cacheDir.path}/plan-b-content",
        restoreMetadata = { restoreNativeCachedMetadata(audioRepository, platformMediaSource, it) }, io = Dispatchers.IO)
    return VLCPlayback(audioRepository, history, platformMediaSource, dataTracker, playbackFailureRepository,
        scope = scope, contentAccess = content, continuousOutput = true)
}

class VLCPlayback internal constructor(
    audioRepository: AudioRepository,
    history: PlaybackHistory,
    platformMediaSource: PlatformMediaSource,
    dataTracker: IPlaybackDataTracker,
    playbackFailureRepository: PlaybackFailureRepository,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob()),
    nativePlayerProvider: suspend () -> MediaPlayer = {
        VLCPlayerLoader.initialize().await()
        checkNotNull(VLCPlayer.getPlayer()) { "VLC player initialization failed" }
    },
    installPlatformControls: (VLCPlayback) -> Unit = { playback ->
        if (NativeExtractor.isMac()) { MacOSMenu(playback); MacOSNotification(playback, platformMediaSource) }
    },
    contentAccess: NativeContentAccess? = null,
    continuousOutput: Boolean = false,
    private val engine: PlaybackEngine = if (continuousOutput) VlcPcmEngine(scope, {
        VLCPlayerLoader.initialize().await(); VLCPlayer.newAudioPlayer()
    }, contentAccess, fallbackFactory = { VlcEngine(scope, nativePlayerProvider, contentAccess) }) else VlcEngine(scope, nativePlayerProvider, contentAccess),
) : CorePlayback(scope, EngineAudioRuntime(scope, listOf(engine),
    volume = { _, v -> when (engine) { is VlcEngine -> engine.setVolume(v); is VlcPcmEngine -> engine.setVolume(v) } },
    canFade = { true }, contentAccess = contentAccess),
    platformMediaSource, audioRepository, history, dataTracker, playbackFailureRepository, ::classifyVlcLoadFailure) {
    val player: MediaPlayer get() = checkNotNull(when (engine) { is VlcEngine -> engine.player; is VlcPcmEngine -> engine.player; else -> null }) { "VLC player has not loaded media" }
    init {
        com.lalilu.lplayer.core.PlaybackOutput.report(com.lalilu.playback.core.OutputCapabilities(
            backend = if (continuousOutput) "VLC / 共享 PCM 输出" else "VLC / 系统音频输出", original = com.lalilu.playback.core.Support.Unsupported,
            originalReason = "VLC 当前未提供可验证的独占、系统重采样旁路及实际输出格式",
            gapless = com.lalilu.playback.core.Support.Unknown,
            apiFormat = if (continuousOutput) com.lalilu.playback.core.OutputFormat(PCM_RATE, 2, "PCM 16-bit") else null,
            apiFormatEvidence = if (continuousOutput) "应用 PCM 输出配置；不是物理 DAC 格式证明" else null))
        scope.launch { installPlatformControls(this@VLCPlayback); startHistoryPlayback() }
    }
}

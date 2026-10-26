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

@Single(binds = [Playback::class])
fun provideVLCPlayback(audioRepository: AudioRepository, history: PlaybackHistory,
    platformMediaSource: PlatformMediaSource, dataTracker: IPlaybackDataTracker,
    playbackFailureRepository: PlaybackFailureRepository): VLCPlayback =
    VLCPlayback(audioRepository, history, platformMediaSource, dataTracker, playbackFailureRepository)

class VLCPlayback internal constructor(
    audioRepository: AudioRepository,
    history: PlaybackHistory,
    platformMediaSource: PlatformMediaSource,
    dataTracker: IPlaybackDataTracker,
    playbackFailureRepository: PlaybackFailureRepository,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob()),
    nativePlayerProvider: suspend () -> MediaPlayer = {
        VLCPlayerLoader.initialize().join()
        checkNotNull(VLCPlayer.getPlayer()) { "VLC player initialization failed" }
    },
    installPlatformControls: (VLCPlayback) -> Unit = { playback ->
        if (NativeExtractor.isMac()) { MacOSMenu(playback); MacOSNotification(playback) }
    },
    private val engine: VlcEngine = VlcEngine(scope, nativePlayerProvider),
) : CorePlayback(scope, EngineAudioRuntime(scope, listOf(engine),
    volume = { _, v -> engine.setVolume(v) }, canFade = { true }),
    platformMediaSource, audioRepository, history, dataTracker, playbackFailureRepository, ::classifyVlcLoadFailure) {
    val player: MediaPlayer get() = checkNotNull(engine.player) { "VLC player has not loaded media" }
    init {
        com.lalilu.lplayer.core.PlaybackOutput.report(com.lalilu.playback.core.OutputCapabilities(
            backend = "VLC / 系统音频输出", original = com.lalilu.playback.core.Support.Unsupported,
            originalReason = "VLC 当前未提供可验证的独占、系统重采样旁路及实际输出格式",
            gapless = com.lalilu.playback.core.Support.Unsupported))
        scope.launch { installPlatformControls(this@VLCPlayback); startHistoryPlayback() }
    }
}

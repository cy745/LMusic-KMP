package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.repository.*
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lplayer.core.*
import com.lalilu.lplayer.helper.AudioSessionHelper
import com.lalilu.lplayer.notifacation.NowPlayingInfoNotification
import com.lalilu.lplayer.notifacation.RemoteCommandHandler
import kotlinx.coroutines.*
import org.koin.core.annotation.Single
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.path
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout

@Single(binds = [Playback::class])
fun provideAVPlayerPlayback(history: PlaybackHistory, audioRepository: AudioRepository,
    platformMediaSource: PlatformMediaSource, tracker: IPlaybackDataTracker, playbackFailureRepository: PlaybackFailureRepository): CorePlayback {
    val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    val content = NativeContentAccess(scope, platformMediaSource, audioRepository,
        HttpClient(Darwin) { install(HttpTimeout) { connectTimeoutMillis = 15000; socketTimeoutMillis = 15000; requestTimeoutMillis = 30000 } },
        "${FileKit.filesDir.path}/plan-b-catalog", "${FileKit.cacheDir.path}/plan-b-content",
        restoreMetadata = { restoreNativeCachedMetadata(audioRepository, platformMediaSource, it) }, io = Dispatchers.IO)
    val runtime = EngineAudioRuntime(scope, listOf(MusicKitEngine(), AVAudioPlayerEngine(), AVPlayerEngine(content)),
        volume = { engine, value -> when (engine) {
            is AVPlayerEngine -> engine.setVolume(value)
            is AVAudioPlayerEngine -> engine.setVolume(value)
        } }, canFade = { it !is MusicKitEngine }, contentAccess = content)
    return CorePlayback(scope, runtime, platformMediaSource, audioRepository, history, tracker,
        failureRepository = playbackFailureRepository, classifyFailure = ::classifyIosLoadFailure).also { playback ->
        NowPlayingInfoNotification.bindPlayback(playback, platformMediaSource)
        RemoteCommandHandler.bindPlayback(playback)
        if (AudioSessionHelper.setUpAudioSession()) AudioSessionHelper.bindPlayback(playback,
            shouldHandleInterruption = { runtime.activeEngine !is MusicKitEngine })
        com.lalilu.lplayer.helper.IosOutputProbe.start()
        playback.startHistoryPlayback()
    }
}

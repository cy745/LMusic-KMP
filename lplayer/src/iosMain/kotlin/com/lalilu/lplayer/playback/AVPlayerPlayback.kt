package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.repository.*
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lplayer.core.*
import com.lalilu.lplayer.helper.AudioSessionHelper
import com.lalilu.lplayer.notifacation.NowPlayingInfoNotification
import com.lalilu.lplayer.notifacation.RemoteCommandHandler
import kotlinx.coroutines.*
import org.koin.core.annotation.Single

@Single(binds = [Playback::class])
fun provideAVPlayerPlayback(history: PlaybackHistory, audioRepository: AudioRepository,
    platformMediaSource: PlatformMediaSource, playbackFailureRepository: PlaybackFailureRepository): CorePlayback {
    val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    val runtime = EngineAudioRuntime(scope, listOf(MusicKitEngine(), AVAudioPlayerEngine(), AVPlayerEngine()),
        volume = { engine, value -> when (engine) {
            is AVPlayerEngine -> engine.setVolume(value)
            is AVAudioPlayerEngine -> engine.setVolume(value)
        } }, canFade = { it !is MusicKitEngine })
    return CorePlayback(scope, runtime, platformMediaSource, audioRepository, history,
        failureRepository = playbackFailureRepository, classifyFailure = ::classifyIosLoadFailure).also { playback ->
        NowPlayingInfoNotification.bindPlayback(playback)
        RemoteCommandHandler.bindPlayback(playback)
        if (AudioSessionHelper.setUpAudioSession()) AudioSessionHelper.bindPlayback(playback,
            shouldHandleInterruption = { runtime.activeEngine !is MusicKitEngine })
        com.lalilu.lplayer.helper.IosOutputProbe.start()
        playback.startHistoryPlayback()
    }
}

package com.lalilu.lplayer.notification

import co.touchlab.kermit.Logger
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import com.lalilu.lplayer.core.*
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.model.mediaKey
import com.lalilu.lmedia.domain.model.MediaKey
import org.rococoa.Foundation
import com.lalilu.lplayer.macos.*
import com.lalilu.lplayer.menu.FoundationCallback
import com.lalilu.lplayer.playback.Playback
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.lalilu.lplayer.action.launchPlayerAction
import org.rococoa.Rococoa
import org.rococoa.cocoa.foundation.*
import kotlin.coroutines.CoroutineContext

fun interface CommandHandler {
    fun handleCommand(event: MPRemoteCommandEvent): MPRemoteCommandHandlerStatus
}

fun interface SeekPositionCommandHandler {
    fun onPositionChange(event: MPChangePlaybackPositionCommandEvent): MPRemoteCommandHandlerStatus
}

class MacOSNotification(
    private val playback: Playback,
    private val sources: PlatformMediaSource? = null,
) : CoroutineScope, CommandHandler, SeekPositionCommandHandler {
    companion object { private var active: MacOSNotification? = null }

    override val coroutineContext: CoroutineContext = Dispatchers.Default.limitedParallelism(1) + SupervisorJob()
    private val nowPlayingInfoCenter by lazy { MPNowPlayingInfoCenter.defaultCenter() }
    private val remoteCommandCenter by lazy { MPRemoteCommandCenter.sharedCommandCenter() }
    private val nsCallback by lazy { FoundationCallback.wrap(this, ::handleCommand) }
    private val enableCommand by lazy {
        listOf(
            remoteCommandCenter.playCommand(),
            remoteCommandCenter.pauseCommand(),
            remoteCommandCenter.stopCommand(),
            remoteCommandCenter.togglePlayPauseCommand(),
            remoteCommandCenter.nextTrackCommand(),
            remoteCommandCenter.previousTrackCommand(),
        )
    }
    private val imageLoader by lazy { SingletonImageLoader.get(PlatformContext.INSTANCE) }
    private var snapshot = SystemMediaSnapshot(null, 0, 0, false)
    private var artworkKey: MediaKey? = null
    private var artwork: NSObject? = null
    private val changePositionCallback by lazy { FoundationCallback.wrap(this, ::onPositionChange) }

    private fun publish() {
        val audio = snapshot.audio
        val entries = linkedMapOf<NSObject, NSObject>(
            MPMediaItemProperty.Title.nativeValue to NSString.stringWithString(audio?.title ?: ""),
            MPMediaItemProperty.Artist.nativeValue to NSString.stringWithString(audio?.subtitle ?: ""),
            MPMediaItemProperty.PlaybackDuration.nativeValue to NSNumber.CLASS.numberWithDouble(snapshot.durationMs / 1000.0),
            MPNowPlayingInfoProperty.ElapsedPlaybackTime.nativeValue to NSNumber.CLASS.numberWithDouble(snapshot.positionMs / 1000.0),
            MPNowPlayingInfoProperty.PlaybackRate.nativeValue to NSNumber.CLASS.numberWithDouble(if (snapshot.playing) 1.0 else 0.0),
        )
        if (audio?.mediaKey == artworkKey) artwork?.let { entries[MPMediaItemProperty.Artwork.nativeValue] = it }
        val info = NSDictionary.CLASS.dictionaryWithObjects_forKeys(
            NSArray.CLASS.arrayWithObjects(*entries.values.toTypedArray()),
            NSArray.CLASS.arrayWithObjects(*entries.keys.toTypedArray()))
        Foundation.runOnMainThread { if (isActive) nowPlayingInfoCenter.setNowPlayingInfo(info) }
    }

    init {
        synchronized(MacOSNotification::class.java) { active?.close(); active = this }
        WrapperLibrary.instance
        enableCommand.forEach { command ->
            command.addTarget(
                target = nsCallback.target.id(),
                selector = nsCallback.selector
            )
        }

        remoteCommandCenter.changePlaybackPositionCommand().addTarget(
            target = changePositionCallback.target.id(),
            selector = changePositionCallback.selector
        )

        playback.systemMediaSnapshots().onEach { snapshot = it; publish() }.launchIn(this)
        launch {
            playback.systemArtworkRequests(sources).collectLatest { request ->
                artworkKey = null
                artwork = null
                publish()
                if (request == null) return@collectLatest
                try {
                    val result = imageLoader.execute(ImageRequest.Builder(PlatformContext.INSTANCE)
                        .data(request).size(256, 256).build())
                    ensureActive()
                    if (playback.queue.currentItem()?.mediaKey != request.audio.mediaKey) return@collectLatest
                    val bitmap = result.image?.toBitmap()
                    val pixels = bitmap?.peekPixels()?.buffer
                    val data = pixels?.takeIf { it.size > 0 }?.let { NSData.CLASS.dataWithBytes_length(it.bytes, it.size) }
                    artwork = data?.let {
                        Rococoa.wrap(WrapperLibrary.instance.createMediaItemArtwork(it.id(), bitmap.width,
                            bitmap.height, 32, 8, bitmap.width * 4, 6), NSObject::class.java)
                    }
                    artworkKey = request.audio.mediaKey
                    publish()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { Logger.w("System artwork load failed", error) }
            }
        }
    }

    fun close() {
        cancel()
        enableCommand.forEach { it.removeTarget(nsCallback.target.id(), nsCallback.selector) }
        remoteCommandCenter.changePlaybackPositionCommand().removeTarget(changePositionCallback.target.id(), changePositionCallback.selector)
        FoundationCallback.REFERENCE_MAP.remove(nsCallback.target)
        FoundationCallback.REFERENCE_MAP.remove(changePositionCallback.target)
    }

    override fun handleCommand(event: MPRemoteCommandEvent): MPRemoteCommandHandlerStatus {
        Logger.i("event: ${event.command()} timestamp: ${event.timestamp()}")
        val command = event.command()

        when (command) {
            remoteCommandCenter.playCommand() -> launchPlayerAction { playback.play() }
            remoteCommandCenter.pauseCommand() -> launchPlayerAction { playback.pause() }
            remoteCommandCenter.stopCommand() -> launchPlayerAction { playback.stop() }
            remoteCommandCenter.togglePlayPauseCommand() -> launchPlayerAction { playback.togglePlayPause() }
            remoteCommandCenter.nextTrackCommand() -> launchPlayerAction { playback.skipToNext() }
            remoteCommandCenter.previousTrackCommand() -> launchPlayerAction { playback.skipToPrevious() }
            else -> {
                Logger.i("UnRecognized command: $command timestamp: ${event.timestamp()}")
            }
        }

        return MPRemoteCommandHandlerStatus.Success
    }

    override fun onPositionChange(event: MPChangePlaybackPositionCommandEvent): MPRemoteCommandHandlerStatus {
        val seconds = event.positionTime()
        if (seconds.isFinite() && seconds >= 0) launchPlayerAction { playback.seekTo((seconds * 1000).toLong()) }
        return MPRemoteCommandHandlerStatus.Success
    }
}

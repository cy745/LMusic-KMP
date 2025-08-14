package com.lalilu.lplayer.notification

import co.touchlab.kermit.Logger
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import com.lalilu.common.ext.io
import com.lalilu.lplayer.macos.*
import com.lalilu.lplayer.menu.FoundationCallback
import com.lalilu.lplayer.playback.Playback
import com.lalilu.wrapper.WrapperLibrary
import com.sun.jna.Pointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import org.rococoa.ID
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
    private val playback: Playback
) : CoroutineScope, CommandHandler, SeekPositionCommandHandler {
    override val coroutineContext: CoroutineContext = Dispatchers.io + SupervisorJob()
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
            remoteCommandCenter.changeRepeatModeCommand(),
            remoteCommandCenter.changeShuffleModeCommand(),
            remoteCommandCenter.changePlaybackRateCommand(),
            remoteCommandCenter.seekBackwardCommand(),
            remoteCommandCenter.seekForwardCommand(),
            remoteCommandCenter.ratingCommand(),
            remoteCommandCenter.likeCommand(),
            remoteCommandCenter.dislikeCommand(),
            remoteCommandCenter.bookmarkCommand(),
            remoteCommandCenter.enableLanguageOptionCommand(),
            remoteCommandCenter.disableLanguageOptionCommand(),
        )
    }
    private val imageLoader by lazy { SingletonImageLoader.get(PlatformContext.INSTANCE) }

    init {
        WrapperLibrary.instance
        enableCommand.forEach { command ->
            command.addTarget(
                target = nsCallback.target.id(),
                selector = nsCallback.selector
            )
        }

        val changePositionCallback = FoundationCallback.wrap(this, ::onPositionChange)
        remoteCommandCenter.changePlaybackPositionCommand().addTarget(
            target = changePositionCallback.target.id(),
            selector = changePositionCallback.selector
        )

        playback.currentItem().combine(playback.isPlaying()) { audio, isPlaying ->
            val title = audio?.title ?: "Unknown"
            val subtitle = audio?.subtitle ?: "sub"

            val imageResult = imageLoader.execute(
                ImageRequest.Builder(PlatformContext.INSTANCE)
                    .data(audio)
                    .build()
            )
            val bitmap = imageResult.image?.toBitmap()
            val pixels = bitmap?.peekPixels()?.buffer

            val nsData = pixels?.takeIf { pixels.size > 0 }
                ?.let { NSData.CLASS.dataWithBytes_length(it.bytes, it.size) }

            val artwork = nsData?.let { data ->
                WrapperLibrary.instance.createMediaItemArtwork(
                    Pointer.createConstant(data.id().toLong()),
                    bitmap.width,
                    bitmap.height,
                    32,
                    8,
                    bitmap.width * 4
                ).takeIf { it.getLong(0) > 0 }
                    ?.let { Rococoa.wrap(ID.fromLong(it.getLong(0)), NSObject::class.java) }
            }
            Logger.i("artwork: $artwork")

            val keys = NSArray.CLASS.arrayWithObjects(
                MPMediaItemProperty.Title.nativeValue,
                MPMediaItemProperty.Artist.nativeValue,
                MPMediaItemProperty.PlaybackDuration.nativeValue,
                MPNowPlayingInfoProperty.PlaybackRate.nativeValue,
                MPNowPlayingInfoProperty.ElapsedPlaybackTime.nativeValue,
                MPNowPlayingInfoProperty.IsLiveStream.nativeValue,
                if (artwork == null) null else MPMediaItemProperty.Artwork.nativeValue,
            )
            val values = NSArray.CLASS.arrayWithObjects(
                NSString.stringWithString(title),
                NSString.stringWithString(subtitle),
                NSNumber.CLASS.numberWithLong(playback.currentDuration()),
                NSNumber.CLASS.numberWithDouble(if (isPlaying) 1.0 else 0.0),
                NSNumber.CLASS.numberWithLong(playback.currentPosition()),
                NSNumber.CLASS.numberWithBool(false),
                artwork,
            )

            val dictionary = NSDictionary.CLASS.dictionaryWithObjects_forKeys(values, keys)
            nowPlayingInfoCenter.setNowPlayingInfo(dictionary)

            Logger.i("currentItem: $audio, isPlaying: $isPlaying")
        }.launchIn(this)
    }

    override fun handleCommand(event: MPRemoteCommandEvent): MPRemoteCommandHandlerStatus {
        Logger.i("event: ${event.command()} timestamp: ${event.timestamp()}")
        val command = event.command()

        when (command) {
            remoteCommandCenter.playCommand() -> playback.play()
            remoteCommandCenter.pauseCommand() -> playback.pause()
            remoteCommandCenter.stopCommand() -> playback.stop()
            remoteCommandCenter.togglePlayPauseCommand() -> playback.togglePlayPause()
            remoteCommandCenter.nextTrackCommand() -> playback.skipToNext()
            remoteCommandCenter.previousTrackCommand() -> playback.skipTpPrevious()
            else -> {
                Logger.i("UnRecognized command: $command timestamp: ${event.timestamp()}")
            }
        }

        return MPRemoteCommandHandlerStatus.Success
    }

    override fun onPositionChange(event: MPChangePlaybackPositionCommandEvent): MPRemoteCommandHandlerStatus {
        val position = (event.positionTime() * 1000L).toLong()
        playback.seekTo(position)
        return MPRemoteCommandHandlerStatus.Success
    }
}
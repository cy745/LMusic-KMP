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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
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

        playback.currentItem.combine(playback.isPlaying) { audio, isPlaying ->
            Logger.i("currentItem: $audio, isPlaying: $isPlaying")

            val title = audio?.title ?: "Unknown"
            val subtitle = audio?.subtitle ?: "sub"

            val imageResult = audio?.let {
                imageLoader.execute(
                    ImageRequest.Builder(PlatformContext.INSTANCE)
                        .data(audio)
                        .build()
                )
            }
            val bitmap = imageResult?.image?.toBitmap()
            val pixels = bitmap?.peekPixels()?.buffer

            val nsData = pixels?.takeIf { pixels.size > 0 }
                ?.let { NSData.CLASS.dataWithBytes_length(it.bytes, it.size) }

            val artwork = nsData?.let { data ->
                val result = WrapperLibrary.instance.createMediaItemArtwork(
                    bitmapData = nsData.id(),
                    bitmapWidth = bitmap.width,
                    bitmapHeight = bitmap.height,
                    bitsPerPixel = 32,
                    bitsPerComponent = 8,
                    bytesPerRow = bitmap.width * 4,
                    bitmapInfoType = 6
                )
                Rococoa.wrap(result, NSObject::class.java)
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
                NSNumber.CLASS.numberWithLong(playback.currentDuration.value),
                NSNumber.CLASS.numberWithDouble(if (isPlaying) 1.0 else 0.0),
                NSNumber.CLASS.numberWithLong(playback.currentPosition.value),
                NSNumber.CLASS.numberWithBool(false),
                artwork,
            )

            val dictionary = NSDictionary.CLASS.dictionaryWithObjects_forKeys(values, keys)
            nowPlayingInfoCenter.setNowPlayingInfo(dictionary)
        }.launchIn(this)
    }

    override fun handleCommand(event: MPRemoteCommandEvent): MPRemoteCommandHandlerStatus {
        Logger.i("event: ${event.command()} timestamp: ${event.timestamp()}")
        val command = event.command()

        when (command) {
            remoteCommandCenter.playCommand() -> launch { playback.play() }
            remoteCommandCenter.pauseCommand() -> launch { playback.pause() }
            remoteCommandCenter.stopCommand() -> launch { playback.stop() }
            remoteCommandCenter.togglePlayPauseCommand() -> launch { playback.togglePlayPause() }
            remoteCommandCenter.nextTrackCommand() -> launch { playback.skipToNext() }
            remoteCommandCenter.previousTrackCommand() -> launch { playback.skipToPrevious() }
            else -> {
                Logger.i("UnRecognized command: $command timestamp: ${event.timestamp()}")
            }
        }

        return MPRemoteCommandHandlerStatus.Success
    }

    override fun onPositionChange(event: MPChangePlaybackPositionCommandEvent): MPRemoteCommandHandlerStatus {
        val position = (event.positionTime() * 1000L).toLong()
        launch { playback.seekTo(position) }
        return MPRemoteCommandHandlerStatus.Success
    }
}
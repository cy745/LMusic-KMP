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
        MediaPlayerLibrary.load()
        PlatformContext.INSTANCE

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
            val size = (bitmap?.width ?: 0) * (bitmap?.height ?: 0) * (bitmap?.bytesPerPixel ?: 0)
            val pixels = bitmap?.peekPixels()?.buffer

            if (pixels != null && pixels.size > 0) {
                NSData.CLASS.dataWithBytes_length(pixels.bytes, pixels.size)
            }

            Logger.i("width: ${bitmap?.width}, height: ${bitmap?.height}, bpp: ${bitmap?.bytesPerPixel}, size: $size")

            val keys = NSArray.CLASS.arrayWithObjects(
                MPMediaItemProperty.Title.nativeValue,
                MPMediaItemProperty.Artist.nativeValue,
                MPMediaItemProperty.PlaybackDuration.nativeValue,
//                MPMediaItemProperty.Artwork.nativeValue,
                MPNowPlayingInfoProperty.PlaybackRate.nativeValue,
                MPNowPlayingInfoProperty.ElapsedPlaybackTime.nativeValue,
                MPNowPlayingInfoProperty.IsLiveStream.nativeValue
            )
            val values = NSArray.CLASS.arrayWithObjects(
                NSString.stringWithString(title),
                NSString.stringWithString(subtitle),
                NSNumber.CLASS.numberWithLong(playback.currentDuration()),
//                artwork,
                NSNumber.CLASS.numberWithDouble(if (isPlaying) 1.0 else 0.0),
                NSNumber.CLASS.numberWithLong(playback.currentPosition()),
                NSNumber.CLASS.numberWithBool(false)
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
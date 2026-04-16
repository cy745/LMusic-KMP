package com.lalilu.lplayer.notification

import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import com.lalilu.common.ext.io
import com.lalilu.lplayer.macos.*
import com.lalilu.lplayer.menu.FoundationCallback
import com.lalilu.lplayer.playback.Playback
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    private val logger = KotlinLogging.logger("MacOSNotification")

    private val playInfoDictionary by lazy {
        val keys = NSArray.CLASS.arrayWithObjects(
            MPMediaItemProperty.Title.nativeValue,
            MPMediaItemProperty.Artist.nativeValue,
            MPMediaItemProperty.PlaybackDuration.nativeValue,
            MPNowPlayingInfoProperty.PlaybackRate.nativeValue,
            MPNowPlayingInfoProperty.ElapsedPlaybackTime.nativeValue,
            MPNowPlayingInfoProperty.IsLiveStream.nativeValue,
        )
        val values = NSArray.CLASS.arrayWithObjects(
            NSString.stringWithString(""),
            NSString.stringWithString(""),
            NSNumber.CLASS.numberWithLong(0L),
            NSNumber.CLASS.numberWithDouble(0.0),
            NSNumber.CLASS.numberWithLong(0L),
            NSNumber.CLASS.numberWithBool(false),
        )
        val dictionary = NSDictionary.CLASS.dictionaryWithObjects_forKeys(values, keys)
        dictionary.mutableCopy()
    }

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

        playback.isPlaying.onEach { isPlaying ->
            playInfoDictionary.setValue(
                key = MPNowPlayingInfoProperty.PlaybackRate.nativeValue,
                value = NSNumber.CLASS.numberWithDouble(if (isPlaying) 1.0 else 0.0),
            )
            playInfoDictionary.setValue(
                key = MPNowPlayingInfoProperty.ElapsedPlaybackTime.nativeValue,
                value = NSNumber.CLASS.numberWithLong(playback.currentPosition() / 1000L)
            )
            nowPlayingInfoCenter.setNowPlayingInfo(playInfoDictionary)
        }.launchIn(this)

        playback.currentDuration.onEach {
            playInfoDictionary.setValue(
                key = MPMediaItemProperty.PlaybackDuration.nativeValue,
                value = NSNumber.CLASS.numberWithLong(it / 1000L)
            )
            nowPlayingInfoCenter.setNowPlayingInfo(playInfoDictionary)
        }.launchIn(this)

        playback.currentItem.onEach { audio ->
            logger.info { "currentItem: $audio" }

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
                    bitmapData = data.id(),
                    bitmapWidth = bitmap.width,
                    bitmapHeight = bitmap.height,
                    bitsPerPixel = 32,
                    bitsPerComponent = 8,
                    bytesPerRow = bitmap.width * 4,
                    bitmapInfoType = 6
                )
                Rococoa.wrap(result, NSObject::class.java)
            }

            playInfoDictionary.setValue(
                key = MPMediaItemProperty.Title.nativeValue,
                value = NSString.stringWithString(title),
            )
            playInfoDictionary.setValue(
                key = MPMediaItemProperty.Artist.nativeValue,
                value = NSString.stringWithString(subtitle),
            )
            playInfoDictionary.setValue(
                key = MPMediaItemProperty.PlaybackDuration.nativeValue,
                value = NSNumber.CLASS.numberWithLong(playback.currentDuration.value / 1000L)
            )
            playInfoDictionary.setValue(
                key = MPNowPlayingInfoProperty.ElapsedPlaybackTime.nativeValue,
                value = NSNumber.CLASS.numberWithLong(playback.currentPosition() / 1000L)
            )
            if (artwork != null) {
                playInfoDictionary.setValue(
                    key = MPMediaItemProperty.Artwork.nativeValue,
                    value = artwork
                )
            }
            nowPlayingInfoCenter.setNowPlayingInfo(playInfoDictionary)
        }.launchIn(this)
    }

    override fun handleCommand(event: MPRemoteCommandEvent): MPRemoteCommandHandlerStatus {
        logger.info { "event: ${event.command()} timestamp: ${event.timestamp()}" }
        val command = event.command()

        when (command) {
            remoteCommandCenter.playCommand() -> launch { playback.play() }
            remoteCommandCenter.pauseCommand() -> launch { playback.pause() }
            remoteCommandCenter.stopCommand() -> launch { playback.stop() }
            remoteCommandCenter.togglePlayPauseCommand() -> launch { playback.togglePlayPause() }
            remoteCommandCenter.nextTrackCommand() -> launch { playback.skipToNext() }
            remoteCommandCenter.previousTrackCommand() -> launch { playback.skipToPrevious() }
            else -> {
                logger.info { "UnRecognized command: $command timestamp: ${event.timestamp()}" }
            }
        }

        return MPRemoteCommandHandlerStatus.Success
    }

    override fun onPositionChange(event: MPChangePlaybackPositionCommandEvent): MPRemoteCommandHandlerStatus {
        val position = (event.positionTime() * 1000L).toLong()
        launch {
            playback.seekTo(position)
            updatePosition(event.positionTime().toLong())
        }
        return MPRemoteCommandHandlerStatus.Success
    }

    private fun updatePosition(newPosition: Long) {
        playInfoDictionary.setValue(
            key = MPNowPlayingInfoProperty.ElapsedPlaybackTime.nativeValue,
            value = NSNumber.CLASS.numberWithLong(newPosition)
        )
        nowPlayingInfoCenter.setNowPlayingInfo(playInfoDictionary)
    }
}
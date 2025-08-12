package com.lalilu.lplayer.notification

import com.lalilu.common.ext.io
import com.lalilu.lplayer.macos.MPNowPlayingInfoCenter
import com.lalilu.lplayer.macos.MPRemoteCommandCenter
import com.lalilu.lplayer.macos.MediaPlayerLibrary
import com.lalilu.lplayer.menu.FoundationCallbackRegistry
import com.lalilu.lplayer.menu.ObjcCallback
import com.lalilu.lplayer.playback.Playback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.rococoa.ID
import kotlin.coroutines.CoroutineContext

class MacOSNotification(
    private val playback: Playback
) : CoroutineScope, ObjcCallback {
    override val coroutineContext: CoroutineContext = Dispatchers.io + SupervisorJob()
    private val nowPlayingInfoCenter by lazy { MPNowPlayingInfoCenter.defaultCenter() }
    private val remoteCommandCenter by lazy { MPRemoteCommandCenter.sharedCommandCenter() }
    private val nsCallback by lazy { FoundationCallbackRegistry.registerCallback(this) }

    init {
        MediaPlayerLibrary.load()
//        remoteCommandCenter.playCommand().addTarget(
//            target = nsCallback.target.id(),
//            selector = nsCallback.selector
//        )
//        remoteCommandCenter.pauseCommand().addTarget(
//            target = nsCallback.target.id(),
//            selector = nsCallback.selector
//        )
//        remoteCommandCenter.togglePlayPauseCommand().addTarget(
//            target = nsCallback.target.id(),
//            selector = nsCallback.selector
//        )
//        remoteCommandCenter.stopCommand().addTarget(
//            target = nsCallback.target.id(),
//            selector = nsCallback.selector
//        )
//        remoteCommandCenter.nextTrackCommand().addTarget(
//            target = nsCallback.target.id(),
//            selector = nsCallback.selector
//        )
//        remoteCommandCenter.previousTrackCommand().addTarget(
//            target = nsCallback.target.id(),
//            selector = nsCallback.selector
//        )
//        remoteCommandCenter.changeRepeatModeCommand().addTarget(
//            target = nsCallback.target.id(),
//            selector = nsCallback.selector
//        )
//        remoteCommandCenter.changeShuffleModeCommand().addTarget(
//            target = nsCallback.target.id(),
//            selector = nsCallback.selector
//        )
//        remoteCommandCenter.changePlaybackRateCommand().addTarget(
//            target = nsCallback.target.id(),
//            selector = nsCallback.selector
//        )
//        remoteCommandCenter.seekBackwardCommand().addTarget(
//            target = nsCallback.target.id(),
//            selector = nsCallback.selector
//        )
//        remoteCommandCenter.seekForwardCommand().addTarget(
//            target = nsCallback.target.id(),
//            selector = nsCallback.selector
//        )
//        remoteCommandCenter.changePlaybackRateCommand().addTarget(
//            target = nsCallback.target.id(),
//            selector = nsCallback.selector
//        )
//        remoteCommandCenter.changePlaybackPositionCommand().addTarget(
//            target = nsCallback.target.id(),
//            selector = nsCallback.selector
//        )
//        playback.currentItem()?.onEach { audio ->
//            val title = audio?.title ?: "Unknown"
//            val subtitle = audio?.subtitle ?: "sub"
//            val duration = 3 * 60 * 1000L
//
//            val keys = NSArray.CLASS.arrayWithObjects(
//                MPMediaItemProperty.Title.nativeValue,
//                MPMediaItemProperty.Artist.nativeValue,
//                MPMediaItemProperty.PlaybackDuration.nativeValue,
//                MPNowPlayingInfoProperty.PlaybackRate.nativeValue,
//                MPNowPlayingInfoProperty.ElapsedPlaybackTime.nativeValue,
//                MPNowPlayingInfoProperty.IsLiveStream.nativeValue
//            )
//            val values = NSArray.CLASS.arrayWithObjects(
//                NSString.stringWithString(title),
//                NSString.stringWithString(subtitle),
//                NSNumber.CLASS.numberWithLong(duration),
//                NSNumber.CLASS.numberWithDouble(1.0),
//                NSNumber.CLASS.numberWithLong(10000L),
//                NSNumber.CLASS.numberWithBool(false)
//            )
//
//            val dictionary = NSDictionary.CLASS.dictionaryWithObjects_forKeys(values, keys)
//            nowPlayingInfoCenter.setNowPlayingInfo(dictionary)
//
//            Logger.i("currentItem: $audio")
//        }?.launchIn(this)
    }

    override fun invoke(sender: ID) {
//        Logger.i("event: ${sender.command()} timestamp: ${sender.timestamp()}")
//        return MPRemoteCommandHandlerStatus.Success
    }
}
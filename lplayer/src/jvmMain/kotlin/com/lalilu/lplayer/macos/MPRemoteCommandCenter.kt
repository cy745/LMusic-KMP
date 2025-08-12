package com.lalilu.lplayer.macos

import org.rococoa.cocoa.foundation.NSObject

abstract class MPRemoteCommandCenter : NSObject() {
    companion object {
        fun sharedCommandCenter(): MPRemoteCommandCenter = nsObtain("sharedCommandCenter")
    }

    abstract fun pauseCommand(): MPRemoteCommand
    abstract fun playCommand(): MPRemoteCommand
    abstract fun stopCommand(): MPRemoteCommand
    abstract fun togglePlayPauseCommand(): MPRemoteCommand

    abstract fun nextTrackCommand(): MPRemoteCommand
    abstract fun previousTrackCommand(): MPRemoteCommand
    abstract fun changeRepeatModeCommand(): MPRemoteCommand
    abstract fun changeShuffleModeCommand(): MPRemoteCommand

    abstract fun changePlaybackRateCommand(): MPRemoteCommand
    abstract fun seekBackwardCommand(): MPRemoteCommand
    abstract fun seekForwardCommand(): MPRemoteCommand
    abstract fun changePlaybackPositionCommand(): MPRemoteCommand
    abstract fun ratingCommand(): MPRemoteCommand
    abstract fun likeCommand(): MPRemoteCommand
    abstract fun dislikeCommand(): MPRemoteCommand
    abstract fun bookmarkCommand(): MPRemoteCommand
    abstract fun enableLanguageOptionCommand(): MPRemoteCommand
    abstract fun disableLanguageOptionCommand(): MPRemoteCommand
}
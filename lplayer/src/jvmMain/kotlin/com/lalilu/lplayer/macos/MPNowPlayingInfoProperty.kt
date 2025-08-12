package com.lalilu.lplayer.macos

import org.rococoa.cocoa.foundation.NSString
import org.rococoa.contrib.NativeEnum

sealed class MPNowPlayingInfoProperty(
    private val name: String
) : NativeEnum<NSString> {
    private val value: NSString? = NSString.getGlobalString("MediaPlayer", "MPNowPlayingInfoProperty${name}")
    override fun getNativeValue(): NSString? = value

    data object AdTimeRanges : MPNowPlayingInfoProperty("AdTimeRanges")
    data object AvailableLanguageOptions : MPNowPlayingInfoProperty("AvailableLanguageOptions")
    data object AssetURL : MPNowPlayingInfoProperty("AssetURL")
    data object ChapterCount : MPNowPlayingInfoProperty("ChapterCount")
    data object ChapterNumber : MPNowPlayingInfoProperty("ChapterNumber")
    data object CreditsStartTime : MPNowPlayingInfoProperty("CreditsStartTime")
    data object CurrentLanguageOptions : MPNowPlayingInfoProperty("CurrentLanguageOptions")
    data object CurrentPlaybackDate : MPNowPlayingInfoProperty("CurrentPlaybackDate")
    data object DefaultPlaybackRate : MPNowPlayingInfoProperty("DefaultPlaybackRate")
    data object ElapsedPlaybackTime : MPNowPlayingInfoProperty("ElapsedPlaybackTime")
    data object ExcludeFromSuggestions : MPNowPlayingInfoProperty("ExcludeFromSuggestions")
    data object ExternalContentIdentifier : MPNowPlayingInfoProperty("ExternalContentIdentifier")
    data object ExternalUserProfileIdentifier : MPNowPlayingInfoProperty("ExternalUserProfileIdentifier")
    data object InternationalStandardRecordingCode : MPNowPlayingInfoProperty("InternationalStandardRecordingCode")
    data object IsLiveStream : MPNowPlayingInfoProperty("IsLiveStream")
    data object MediaType : MPNowPlayingInfoProperty("MediaType")
    data object PlaybackProgress : MPNowPlayingInfoProperty("PlaybackProgress")
    data object PlaybackRate : MPNowPlayingInfoProperty("PlaybackRate")
    data object PlaybackQueueCount : MPNowPlayingInfoProperty("PlaybackQueueCount")
    data object PlaybackQueueIndex : MPNowPlayingInfoProperty("PlaybackQueueIndex")
    data object ServiceIdentifier : MPNowPlayingInfoProperty("ServiceIdentifier")
    data object AnimatedArtwork1x1 : MPNowPlayingInfoProperty("1x1AnimatedArtwork")
    data object AnimatedArtwork3x4 : MPNowPlayingInfoProperty("3x4AnimatedArtwork")
}
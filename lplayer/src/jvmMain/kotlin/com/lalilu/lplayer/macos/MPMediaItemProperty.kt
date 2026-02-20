package com.lalilu.lplayer.macos

import org.rococoa.cocoa.foundation.NSString
import org.rococoa.contrib.NativeEnum

sealed class MPMediaItemProperty(
    private val name: String
) : NativeEnum<NSString> {
    private val value: NSString = NSString.getGlobalString("MediaPlayer", "MPMediaItemProperty${name}")
    override fun getNativeValue(): NSString = value

    object AlbumTitle : MPMediaItemProperty("AlbumTitle")
    object AlbumTrackCount : MPMediaItemProperty("AlbumTrackCount")
    object AlbumTrackNumber : MPMediaItemProperty("AlbumTrackNumber")
    object Artist : MPMediaItemProperty("Artist")
    object Artwork : MPMediaItemProperty("Artwork")
    object Composer : MPMediaItemProperty("Composer")
    object DiscCount : MPMediaItemProperty("DiscCount")
    object DiscNumber : MPMediaItemProperty("DiscNumber")
    object Genre : MPMediaItemProperty("Genre")
    object MediaType : MPMediaItemProperty("MediaType")
    object PersistentID : MPMediaItemProperty("PersistentID")
    object PlaybackDuration : MPMediaItemProperty("PlaybackDuration")
    object Title : MPMediaItemProperty("Title")
}
package com.lalilu.lmedia.entity

import com.lalilu.lmedia.SongInfo
import kotlinx.cinterop.ExperimentalForeignApi
import platform.MediaPlayer.MPMediaItem

@OptIn(ExperimentalForeignApi::class)
@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual sealed interface SourceItem {
    data class MPItem(val item: MPMediaItem) : SourceItem
    data class MusicKitItem(val item: SongInfo) : SourceItem
}
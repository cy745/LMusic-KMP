package com.lalilu.lmedia.entity

import com.lalilu.lmedia.SongInfo
import kotlinx.cinterop.ExperimentalForeignApi
import platform.MediaPlayer.MPMediaItem

@OptIn(ExperimentalForeignApi::class)
@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual sealed interface SourceItem {
    actual val key: String

    data class MPItem(val item: MPMediaItem) : SourceItem {
        override val key: String = "${this::class::qualifiedName}_${item.persistentID}"
    }

    data class MusicKitItem(val item: SongInfo) : SourceItem {
        override val key: String = "${this::class::qualifiedName}_${item.title()}_${item.artist()}"
    }
}
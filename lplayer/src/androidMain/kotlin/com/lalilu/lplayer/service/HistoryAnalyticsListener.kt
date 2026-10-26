package com.lalilu.lplayer.service

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.lalilu.lplayer.playback.IPlaybackDataTracker
import com.lalilu.lmedia.domain.model.MediaKey

@UnstableApi
class HistoryAnalyticsListener(val dataTracker: IPlaybackDataTracker) : Player.Listener {
    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int
    ) {
        dataTracker.onMediaItemTransition(
            mediaId = mediaItem?.mediaId?.takeIf { MediaKey.parse(it) != null },
            title = mediaItem?.mediaMetadata?.title.toString(),
            isRepeating = reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT,
            // A selected item may still be loading. Actual Started determines audible duration.
            isNormalTransition = false
        )
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        dataTracker.onIsPlayingChanged(isPlaying)
    }
}

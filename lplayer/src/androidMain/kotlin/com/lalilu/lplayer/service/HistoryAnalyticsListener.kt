package com.lalilu.lplayer.service

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.lalilu.lmedia.data.PlaybackDataTracker

@UnstableApi
class HistoryAnalyticsListener(val dataTracker: PlaybackDataTracker) : AnalyticsListener {
    override fun onMediaItemTransition(
        eventTime: AnalyticsListener.EventTime,
        mediaItem: MediaItem?,
        reason: Int
    ) {
        dataTracker.onMediaItemTransition(
            mediaId = mediaItem?.mediaId,
            title = mediaItem?.mediaMetadata?.title.toString(),
            isRepeating = reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT,
            isNormalTransition = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        )
    }

    override fun onIsPlayingChanged(eventTime: AnalyticsListener.EventTime, isPlaying: Boolean) {
        dataTracker.onIsPlayingChanged(isPlaying)
    }
}
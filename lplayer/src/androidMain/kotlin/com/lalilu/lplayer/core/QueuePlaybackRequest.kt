package com.lalilu.lplayer.core

import android.os.Bundle
import androidx.media3.common.MediaItem

/** One-shot intent on the standard MediaSession setMediaItems request (retains large-list transport). */
internal const val START_QUEUE_PLAYBACK = "com.lalilu.lplayer.start_queue_playback"
internal fun MediaItem.withQueuePlaybackRequest(start: Boolean): MediaItem = buildUpon()
    .setRequestMetadata(requestMetadata.buildUpon().setExtras(Bundle(requestMetadata.extras ?: Bundle.EMPTY).apply {
        if (start) putBoolean(START_QUEUE_PLAYBACK, true) else remove(START_QUEUE_PLAYBACK)
    }).build()).build()

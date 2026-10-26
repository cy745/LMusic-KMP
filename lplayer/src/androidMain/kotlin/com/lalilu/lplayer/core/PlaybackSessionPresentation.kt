package com.lalilu.lplayer.core

import android.os.Bundle
import androidx.media3.session.MediaController
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.playback.core.PlaybackSnapshot

private const val BUFFER_ITEM = "bufferedItem"
private const val BUFFER_END = "bufferedEndMs"

/** Media3 stops periodic position delivery while paused. Extras carry actual cache facts even then. */
data class PlaybackSessionPresentation(
    val pendingNext: String?,
    val pauseAtEnd: Boolean,
    val bufferedItem: String?,
    val bufferedEndMs: Long,
) {
    fun toBundle() = Bundle().apply {
        pendingNext?.let { putString("pendingNext", it) }
        putBoolean("pauseAtEnd", pauseAtEnd)
        bufferedItem?.let { putString(BUFFER_ITEM, it) }
        putLong(BUFFER_END, bufferedEndMs)
    }
}

fun PlaybackSnapshot.sessionPresentation() = PlaybackSessionPresentation(
    pendingNext = explicitNext?.let { MediaKey(it.source, it.id).stableKey },
    pauseAtEnd = pauseAtEnd,
    bufferedItem = selected?.let { MediaKey(it.source, it.id).stableKey },
    bufferedEndMs = bufferedPositionMs,
)

/** Never apply a buffer notification belonging to a different song. Older sessions use standard data. */
fun MediaController.actualBufferedPosition(expectedMediaId: String? = currentMediaItem?.mediaId): Long {
    if (expectedMediaId == null || currentMediaItem?.mediaId != expectedMediaId) return 0L
    val extras = sessionExtras
    return if (currentMediaItem?.mediaId != null && extras.getString(BUFFER_ITEM) == currentMediaItem?.mediaId && extras.containsKey(BUFFER_END)) {
        extras.getLong(BUFFER_END).coerceAtLeast(0L)
    } else bufferedPosition.coerceAtLeast(0L)
}

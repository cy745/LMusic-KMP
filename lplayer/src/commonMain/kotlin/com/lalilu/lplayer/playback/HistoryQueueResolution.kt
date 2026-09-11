package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.mediaKey

/** Keep original slots so missing rows and repeated songs cannot change the selected occurrence. */
internal data class HistoryQueueResolution(val slots: List<LAudio?>, val originalIndex: Int) {
    val items: List<LAudio> get() = slots.filterNotNull()
    val currentIndex: Int get() = if (slots.getOrNull(originalIndex) == null) -1
        else slots.take(originalIndex).count { it != null }
}

internal fun PlaybackHistory.HistorySnapshot.resolveQueue(audios: List<LAudio>): HistoryQueueResolution {
    val byId = audios.groupBy { it.id }
    val slots = ids.mapIndexed { index, id ->
        val candidates = byId[id].orEmpty()
        val source = sourceNames.getOrNull(index)
        if (source != null) candidates.firstOrNull { it.mediaSourceName == source }
        else candidates.distinctBy { it.mediaKey }.singleOrNull()
    }
    return HistoryQueueResolution(slots, index)
}

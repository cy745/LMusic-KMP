package com.lalilu.lplayer.action

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.repository.getPlaybackSlots
import kotlinx.coroutines.flow.first

internal data class QueueSelection(val items: List<LAudio>, val index: Int)

/** Keep requested order; a missing selected song must never silently play another one. */
internal suspend fun AudioRepository.resolveQueueSelection(
    playbackIds: List<String>,
    selectedPlaybackId: String?,
): QueueSelection? {
    val items = getPlaybackSlots(playbackIds).first().filterNotNull()
    if (playbackIds.isNotEmpty() && items.isEmpty()) return null
    val index = selectedPlaybackId?.let { selected -> items.indexOfFirst { it.playbackId == selected } } ?: 0
    if (index < 0) return null
    return QueueSelection(items, index)
}

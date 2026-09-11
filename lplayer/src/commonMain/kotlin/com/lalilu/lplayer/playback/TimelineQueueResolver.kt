package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio

/** A missing row must not shift every subsequent Timeline index. */
internal fun resolveTimelineQueue(
    ids: List<String>,
    resolved: List<LAudio>,
    known: List<LAudio>,
): List<LAudio>? {
    val resolvedById = resolved.associateBy { it.id }
    val knownById = known.associateBy { it.id }
    return ids.map { resolvedById[it] ?: knownById[it] ?: return null }
}

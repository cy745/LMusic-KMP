package com.lalilu.lmedia.domain.repository

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.lmedia.domain.model.mediaKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Never resolve a source-qualified request through the ambiguous raw-ID singleton lookup. */
fun AudioRepository.getAudioByPlaybackId(playbackId: String): Flow<LAudio?> {
    val key = MediaKey.parse(playbackId) ?: return flowOf(null)
    return getAudiosByPlaybackIds(listOf(playbackId)).map { rows -> rows.singleOrNull { it.mediaKey == key } }
}

/** Keep request order and repeated occurrences. Null slots must not shift platform indices. */
fun AudioRepository.getPlaybackSlots(playbackIds: List<String>): Flow<List<LAudio?>> {
    val keys = playbackIds.map(MediaKey::parse)
    val ids = keys.mapNotNull { it?.stableKey }.distinct()
    if (ids.isEmpty()) return flowOf(List(playbackIds.size) { null })
    return getAudiosByPlaybackIds(ids).map { rows ->
        val byKey = rows.groupBy { it.mediaKey }
        keys.map { key -> key?.let { byKey[it]?.singleOrNull() } }
    }
}

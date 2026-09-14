package com.lalilu.lmedia.domain.repository

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.MediaKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface AudioRepository {
    fun getAudios(): Flow<List<LAudio>>
    /** Raw source IDs; multiple sources may contribute rows for the same ID. */
    fun getAudios(ids: List<String>): Flow<List<LAudio>>
    /** Raw-ID singleton lookup. Ambiguous IDs must not arbitrarily choose a source. */
    fun getAudio(id: String): Flow<LAudio?>

    /** Source-qualified lookup. Ordering and missing/duplicate slots belong to getPlaybackSlots. */
    fun getAudiosByPlaybackIds(playbackIds: List<String>): Flow<List<LAudio>> {
        val validIds = playbackIds.filter { MediaKey.parse(it) != null }.toSet()
        val rawIds = validIds.mapNotNull { MediaKey.parse(it)?.id }.distinct()
        return getAudios(rawIds).map { rows -> rows.filter { it.playbackId in validIds } }
    }

    /** 删除不可用歌曲，并清理失去全部歌曲引用的歌手、专辑、流派及关联关系。 */
    suspend fun clearUnavailableAudio()
}

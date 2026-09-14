package com.lalilu.lmedia.domain.usecase

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.repository.getPlaybackSlots
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

/**
 * Unified keyword search for audios.
 * Replaces duplicate filtering logic across SongsState, AlbumDetailState,
 * ArtistDetailState, and PlaylistDetailState.
 */
@org.koin.core.annotation.Single
class SearchAudiosUseCase(
    private val audioRepository: AudioRepository
) {
    /**
     * @param ids Optional filter — only return audios matching these IDs.
     * @param keywords Case-insensitive AND filter across [LAudio.title] + [LAudio.subtitle].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(
        ids: List<String>? = null,
        keywords: List<String> = emptyList(),
        playbackIds: List<String>? = null,
    ): Flow<List<LAudio>> {
        require(ids == null || playbackIds == null) { "Choose raw IDs or playback IDs, not both" }
        val source = when {
            playbackIds != null -> audioRepository.getPlaybackSlots(playbackIds).mapLatest { it.filterNotNull() }
            ids != null -> audioRepository.getAudios(ids)
            else -> audioRepository.getAudios()
        }

        if (keywords.isEmpty()) return source

        return source.mapLatest { items ->
            items.filter { item ->
                val matchText = "${item.title}_${item.subtitle}"
                keywords.all { matchText.contains(it, ignoreCase = true) }
            }
        }
    }
}

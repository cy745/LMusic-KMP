package com.lalilu.lmedia.domain.usecase

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

/**
 * Unified keyword search for audios.
 * Replaces duplicate filtering logic across SongsState, AlbumDetailState,
 * ArtistDetailState, and PlaylistDetailState.
 */
class SearchAudiosUseCase(
    private val audioRepository: AudioRepository
) {
    /**
     * @param ids Optional filter — only return audios matching these IDs.
     * @param keywords Case-insensitive AND filter across [LAudio.getMatchText].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(
        ids: List<String>? = null,
        keywords: List<String> = emptyList()
    ): Flow<List<LAudio>> {
        val source = if (ids != null) {
            audioRepository.getAudios(ids)
        } else {
            audioRepository.getAudios()
        }

        if (keywords.isEmpty()) return source

        return source.mapLatest { items ->
            items.filter { item ->
                val matchText = item.getMatchText()
                keywords.all { matchText.contains(it, ignoreCase = true) }
            }
        }
    }
}

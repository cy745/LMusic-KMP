package com.lalilu.lmedia.domain.usecase

import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.repository.ArtistRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

/**
 * Unified keyword search for artists.
 *
 * Mirrors [SearchAudiosUseCase] but targets [ArtistRepository.getArtists].
 * Case-insensitive AND filter across [LArtist.title] + [LArtist.subtitle].
 *
 * Added as part of Issue #9 (integrated search page).
 */
@org.koin.core.annotation.Single
class SearchArtistsUseCase(
    private val artistRepository: ArtistRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(
        keywords: List<String> = emptyList()
    ): Flow<List<LArtist>> {
        val source = artistRepository.getArtists()

        if (keywords.isEmpty()) return source

        return source.mapLatest { items ->
            items.filter { item ->
                val matchText = "${item.title}_${item.subtitle}"
                keywords.all { matchText.contains(it, ignoreCase = true) }
            }
        }
    }
}
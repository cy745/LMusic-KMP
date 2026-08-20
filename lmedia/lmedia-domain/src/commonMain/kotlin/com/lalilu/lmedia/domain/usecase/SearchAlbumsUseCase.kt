package com.lalilu.lmedia.domain.usecase

import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.domain.repository.AlbumRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

/**
 * Unified keyword search for albums.
 *
 * Mirrors [SearchAudiosUseCase] but targets [AlbumRepository.getAlbums].
 * Case-insensitive AND filter across [LAlbum.title] + [LAlbum.subtitle].
 *
 * Added as part of Issue #9 (integrated search page).
 */
@org.koin.core.annotation.Single
class SearchAlbumsUseCase(
    private val albumRepository: AlbumRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(
        keywords: List<String> = emptyList()
    ): Flow<List<LAlbum>> {
        val source = albumRepository.getAlbums()

        if (keywords.isEmpty()) return source

        return source.mapLatest { items ->
            items.filter { item ->
                val matchText = "${item.title}_${item.subtitle}"
                keywords.all { matchText.contains(it, ignoreCase = true) }
            }
        }
    }
}
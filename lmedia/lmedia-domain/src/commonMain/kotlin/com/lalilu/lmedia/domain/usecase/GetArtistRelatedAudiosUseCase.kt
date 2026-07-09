package com.lalilu.lmedia.domain.usecase

import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.ArtistRepository
import kotlinx.coroutines.flow.firstOrNull

/**
 * Finds [LAudio] entries belonging to a given [LArtist].
 * Delegates to [ArtistRepository.getAudiosByArtist] to query via DB cross-reference.
 */
@org.koin.core.annotation.Single
class GetArtistRelatedAudiosUseCase(
    private val artistRepository: ArtistRepository
) {
    suspend operator fun invoke(artist: LArtist): List<LAudio> {
        return artistRepository.getAudiosByArtist(artist.id).firstOrNull() ?: emptyList()
    }
}

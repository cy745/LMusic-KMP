package com.lalilu.lmedia.domain.usecase

import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.repository.ArtistRepository
import kotlinx.coroutines.flow.firstOrNull

/**
 * Find artists related to a given artist via shared audio tracks.
 * Logic: get all songs of the artist → get all artists of those songs → deduplicate → exclude self.
 * Extracted from ArtistDetailVM.loadRelatedArtists().
 */
class GetRelatedArtistsUseCase(
    private val artistRepository: ArtistRepository
) {
    /**
     * @param artistId The target artist's ID.
     * @return List of related artists (excluding the target artist).
     */
    suspend operator fun invoke(artistId: String): List<LArtist> {
        val artist = artistRepository.getArtist(artistId).firstOrNull() ?: return emptyList()

        // Get all audio IDs associated with this artist
        val audioIds = artistRepository.getAudioIdsByArtist(artistId).firstOrNull() ?: return emptyList()
        if (audioIds.isEmpty()) return emptyList()

        // For each of those audios, find what other artists are associated
        val relatedArtistIds = artistRepository.getArtistIdsByAudioIds(audioIds).firstOrNull() ?: return emptyList()

        // Deduplicate and exclude self
        val uniqueIds = relatedArtistIds
            .distinct()
            .filter { it != artist.idValue() }

        if (uniqueIds.isEmpty()) return emptyList()

        // Fetch full artist data
        return artistRepository.getArtists(uniqueIds).firstOrNull() ?: emptyList()
    }
}

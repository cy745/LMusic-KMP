package com.lalilu.lmedia.domain.usecase

import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.ArtistRepository
import com.lalilu.lmedia.domain.repository.AudioRepository
import kotlinx.coroutines.flow.firstOrNull

/**
 * Finds [LAudio] entries belonging to a given [LArtist].
 * Uses [ArtistRepository.getAudioIdsByArtist] + [AudioRepository.getAudios]
 * to query via DB cross-reference instead of filtering all audios in memory.
 */
@org.koin.core.annotation.Single
class GetArtistRelatedAudiosUseCase(
    private val artistRepository: ArtistRepository,
    private val audioRepository: AudioRepository
) {
    suspend operator fun invoke(artist: LArtist): List<LAudio> {
        val audioIds = artistRepository.getAudioIdsByArtist(artist.id).firstOrNull()
            ?: return emptyList()
        if (audioIds.isEmpty()) return emptyList()
        return audioRepository.getAudios(audioIds).firstOrNull() ?: emptyList()
    }
}

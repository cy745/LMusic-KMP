package com.lalilu.lmedia.domain.usecase

import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import kotlinx.coroutines.flow.firstOrNull

/**
 * Finds [LAudio] entries belonging to a given [LArtist].
 * Matches by [LAudio.metadata.artist] containing the artist name
 * extracted from [LArtist.id]. Supports multi-artist separators (/ ; 、, ,).
 */
@org.koin.core.annotation.Single
class GetArtistRelatedAudiosUseCase(
    private val audioRepository: AudioRepository
) {
    suspend operator fun invoke(artist: LArtist): List<LAudio> {
        val artistName = artist.id.removePrefix(LArtist.ID_PREFIX)
        if (artistName.isBlank()) return emptyList()

        val allAudios = audioRepository.getAudios().firstOrNull() ?: return emptyList()
        return allAudios.filter { audio ->
            audio.metadata.artist?.split('/', ';', '、', ',', '，')?.any {
                it.trim() == artistName
            } ?: false
        }
    }
}

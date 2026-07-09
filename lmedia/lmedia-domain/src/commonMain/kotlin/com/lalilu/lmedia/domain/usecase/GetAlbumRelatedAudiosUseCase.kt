package com.lalilu.lmedia.domain.usecase

import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import kotlinx.coroutines.flow.firstOrNull

/**
 * Finds [LAudio] entries belonging to a given [LAlbum].
 * Matches by [LAudio.metadata.album] against the album name extracted from [LAlbum.id].
 */
@org.koin.core.annotation.Single
class GetAlbumRelatedAudiosUseCase(
    private val audioRepository: AudioRepository
) {
    suspend operator fun invoke(album: LAlbum): List<LAudio> {
        val albumName = album.id.removePrefix(LAlbum.ID_PREFIX)
        if (albumName.isBlank()) return emptyList()

        val allAudios = audioRepository.getAudios().firstOrNull() ?: return emptyList()
        return allAudios.filter { it.metadata.album == albumName }
    }
}

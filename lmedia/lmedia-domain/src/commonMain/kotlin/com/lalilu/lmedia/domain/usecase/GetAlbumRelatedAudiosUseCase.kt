package com.lalilu.lmedia.domain.usecase

import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AlbumRepository
import kotlinx.coroutines.flow.firstOrNull

/**
 * Finds [LAudio] entries belonging to a given [LAlbum].
 * Delegates to [AlbumRepository.getAudiosByAlbum] to query via DB cross-reference.
 */
@org.koin.core.annotation.Single
class GetAlbumRelatedAudiosUseCase(
    private val albumRepository: AlbumRepository
) {
    suspend operator fun invoke(album: LAlbum): List<LAudio> {
        return albumRepository.getAudiosByAlbum(album.id).firstOrNull() ?: emptyList()
    }
}

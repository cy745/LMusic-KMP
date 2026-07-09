package com.lalilu.lmedia.domain.repository

import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.flow.Flow

interface AlbumRepository {
    fun getAlbums(): Flow<List<LAlbum>>
    fun getAlbums(ids: List<String>): Flow<List<LAlbum>>
    fun getAlbum(id: String): Flow<LAlbum?>
    fun getAudiosByAlbum(albumId: String): Flow<List<LAudio>>
}

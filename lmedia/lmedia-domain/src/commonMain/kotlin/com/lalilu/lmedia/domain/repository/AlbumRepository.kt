package com.lalilu.lmedia.domain.repository

import com.lalilu.lmedia.domain.model.LAlbum
import kotlinx.coroutines.flow.Flow

interface AlbumRepository {
    fun getAlbums(): Flow<List<LAlbum>>
    fun getAlbums(ids: List<String>): Flow<List<LAlbum>>
    fun getAlbum(id: String): Flow<LAlbum?>
}

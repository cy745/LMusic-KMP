package com.lalilu.lmedia.data.repository

import com.lalilu.lmedia.data.database.LAlbumDao
import com.lalilu.lmedia.data.mapper.toDomain
import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.domain.repository.AlbumRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import org.koin.core.annotation.Single

@OptIn(ExperimentalCoroutinesApi::class)
@Single(binds = [AlbumRepository::class])
class AlbumRepositoryImpl(
    private val albumDao: LAlbumDao
) : AlbumRepository {

    override fun getAlbums(): Flow<List<LAlbum>> =
        albumDao.getAllAlbum().mapLatest { list -> list.map { it.toDomain() } }

    override fun getAlbums(ids: List<String>): Flow<List<LAlbum>> =
        albumDao.getAlbums(ids).mapLatest { list -> list.map { it.toDomain() } }

    override fun getAlbum(id: String): Flow<LAlbum?> =
        albumDao.getAlbum(id).mapLatest { it?.toDomain() }
}

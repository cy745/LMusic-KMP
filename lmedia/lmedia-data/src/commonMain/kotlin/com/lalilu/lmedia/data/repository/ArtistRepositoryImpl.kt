package com.lalilu.lmedia.data.repository

import com.lalilu.lmedia.data.database.LArtistDao
import com.lalilu.lmedia.data.mapper.toDomain
import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.repository.ArtistRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import org.koin.core.annotation.Single

@OptIn(ExperimentalCoroutinesApi::class)
@Single(binds = [ArtistRepository::class])
class ArtistRepositoryImpl(
    private val artistDao: LArtistDao
) : ArtistRepository {

    override fun getArtists(): Flow<List<LArtist>> =
        artistDao.getAllArtist().mapLatest { list -> list.map { it.toDomain() } }

    override fun getArtists(ids: List<String>): Flow<List<LArtist>> =
        artistDao.getArtists(ids).mapLatest { list -> list.map { it.toDomain() } }

    override fun getArtist(id: String): Flow<LArtist?> =
        artistDao.getArtist(id).mapLatest { it?.toDomain() }
}

package com.lalilu.lmedia.data.repository

import com.lalilu.lmedia.data.database.ILMediaDatabase
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
    private val database: ILMediaDatabase
) : ArtistRepository {
    private val artistDao by lazy { database.artistDao() }

    override fun getArtists(): Flow<List<LArtist>> =
        artistDao.getAllArtist().mapLatest { list -> list.map { it.toDomain() } }

    override fun getArtists(ids: List<String>): Flow<List<LArtist>> =
        artistDao.getArtists(ids).mapLatest { list -> list.map { it.toDomain() } }

    override fun getArtist(id: String): Flow<LArtist?> =
        artistDao.getArtist(id).mapLatest { it?.toDomain() }

    override fun getAudioIdsByArtist(artistId: String): Flow<List<String>> =
        artistDao.getAudiosByArtist(artistId).mapLatest { list -> list.map { it.id } }

    override fun getArtistIdsByAudioIds(audioIds: List<String>): Flow<List<String>> =
        artistDao.getArtistIdsByAudioIds(audioIds)
}

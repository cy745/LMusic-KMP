package com.lalilu.lmedia.data.repository

import com.lalilu.lmedia.data.database.LGenreDao
import com.lalilu.lmedia.data.mapper.toDomain
import com.lalilu.lmedia.domain.model.LGenre
import com.lalilu.lmedia.domain.repository.GenreRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import org.koin.core.annotation.Single

@OptIn(ExperimentalCoroutinesApi::class)
@Single(binds = [GenreRepository::class])
class GenreRepositoryImpl(
    private val genreDao: LGenreDao
) : GenreRepository {

    override fun getGenres(): Flow<List<LGenre>> =
        genreDao.getAllGenre().mapLatest { list -> list.map { it.toDomain() } }

    override fun getGenre(id: String): Flow<LGenre?> =
        genreDao.getGenre(id).mapLatest { it?.toDomain() }
}

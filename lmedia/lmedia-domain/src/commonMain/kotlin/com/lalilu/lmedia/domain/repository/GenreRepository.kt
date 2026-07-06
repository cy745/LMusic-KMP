package com.lalilu.lmedia.domain.repository

import com.lalilu.lmedia.domain.model.LGenre
import kotlinx.coroutines.flow.Flow

interface GenreRepository {
    fun getGenres(): Flow<List<LGenre>>
    fun getGenre(id: String): Flow<LGenre?>
}

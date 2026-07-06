@file:OptIn(ExperimentalCoroutinesApi::class)

package com.lalilu.lmedia.domain.fake

import com.lalilu.lmedia.domain.model.LGenre
import com.lalilu.lmedia.domain.repository.GenreRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest

class FakeGenreRepository : GenreRepository {
    private val store = MutableStateFlow<List<LGenre>>(emptyList())

    fun seed(vararg genres: LGenre) {
        store.value = genres.toList()
    }

    fun clear() {
        store.value = emptyList()
    }

    override fun getGenres(): Flow<List<LGenre>> = store

    override fun getGenre(id: String): Flow<LGenre?> =
        store.mapLatest { list -> list.firstOrNull { it.idValue() == id } }
}

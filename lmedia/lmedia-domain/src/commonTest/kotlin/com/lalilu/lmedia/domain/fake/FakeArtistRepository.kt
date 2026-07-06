package com.lalilu.lmedia.domain.fake

import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.repository.ArtistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest

class FakeArtistRepository : ArtistRepository {
    private val store = MutableStateFlow<List<LArtist>>(emptyList())

    fun seed(vararg artists: LArtist) {
        store.value = artists.toList()
    }

    override fun getArtists(): Flow<List<LArtist>> = store

    override fun getArtists(ids: List<String>): Flow<List<LArtist>> =
        store.mapLatest { list -> list.filter { it.idValue() in ids } }

    override fun getArtist(id: String): Flow<LArtist?> =
        store.mapLatest { list -> list.firstOrNull { it.idValue() == id } }
}

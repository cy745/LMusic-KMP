@file:OptIn(ExperimentalCoroutinesApi::class)

package com.lalilu.lmedia.domain.fake

import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.domain.repository.AlbumRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest

class FakeAlbumRepository : AlbumRepository {
    private val store = MutableStateFlow<List<LAlbum>>(emptyList())

    fun seed(vararg albums: LAlbum) {
        store.value = albums.toList()
    }

    fun clear() {
        store.value = emptyList()
    }

    override fun getAlbums(): Flow<List<LAlbum>> = store

    override fun getAlbums(ids: List<String>): Flow<List<LAlbum>> =
        store.mapLatest { list -> list.filter { it.id in ids } }

    override fun getAlbum(id: String): Flow<LAlbum?> =
        store.mapLatest { list -> list.firstOrNull { it.id == id } }
}

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.lalilu.lmedia.domain.fake

import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.ArtistRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest

class FakeArtistRepository : ArtistRepository {
    private val store = MutableStateFlow<List<LArtist>>(emptyList())
    private val artistAudioMap = mutableMapOf<String, List<String>>()
    private val audioArtistMap = mutableMapOf<String, List<String>>()
    private val artistAudiosMap = mutableMapOf<String, List<LAudio>>()

    fun seed(vararg artists: LArtist) {
        store.value = artists.toList()
    }

    fun seedWithAudioIds(artistId: String, audioIds: List<String>) {
        artistAudioMap[artistId] = audioIds
        audioIds.forEach { audioId ->
            audioArtistMap[audioId] = (audioArtistMap[audioId] ?: emptyList()) + artistId
        }
    }

    fun seedWithAudios(artistId: String, audios: List<LAudio>) {
        artistAudiosMap[artistId] = audios
    }

    fun clear() {
        store.value = emptyList()
        artistAudioMap.clear()
        audioArtistMap.clear()
        artistAudiosMap.clear()
    }

    override fun getArtists(): Flow<List<LArtist>> = store

    override fun getArtists(ids: List<String>): Flow<List<LArtist>> =
        store.mapLatest { list -> list.filter { it.id in ids } }

    override fun getArtist(id: String): Flow<LArtist?> =
        store.mapLatest { list -> list.firstOrNull { it.id == id } }

    override fun getAudiosByArtist(artistId: String): Flow<List<LAudio>> =
        flowOf(artistAudiosMap[artistId] ?: emptyList())

    override fun getAudioIdsByArtist(artistId: String): Flow<List<String>> =
        flowOf(artistAudioMap[artistId] ?: emptyList())

    override fun getArtistIdsByAudioIds(audioIds: List<String>): Flow<List<String>> =
        flowOf(audioIds.flatMap { audioId -> audioArtistMap[audioId] ?: emptyList() })
}

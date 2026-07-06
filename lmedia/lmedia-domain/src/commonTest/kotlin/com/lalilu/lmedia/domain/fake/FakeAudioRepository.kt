@file:OptIn(ExperimentalCoroutinesApi::class)

package com.lalilu.lmedia.domain.fake

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest

class FakeAudioRepository : AudioRepository {
    private val store = MutableStateFlow<List<LAudio>>(emptyList())

    fun seed(vararg audios: LAudio) {
        store.value = audios.toList()
    }

    /** Reset store to empty. Call between tests in `@Before`. */
    fun clear() {
        store.value = emptyList()
    }

    override fun getAudios(): Flow<List<LAudio>> = store

    override fun getAudios(ids: List<String>): Flow<List<LAudio>> =
        store.mapLatest { list -> list.filter { it.idValue() in ids } }

    override fun getAudio(id: String): Flow<LAudio?> =
        store.mapLatest { list -> list.firstOrNull { it.idValue() == id } }
}

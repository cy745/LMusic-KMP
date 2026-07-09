@file:OptIn(ExperimentalCoroutinesApi::class)

package com.lalilu.lmedia.domain.fake

import com.lalilu.lmedia.domain.model.LFolder
import com.lalilu.lmedia.domain.repository.FolderRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest

class FakeFolderRepository : FolderRepository {
    private val store = MutableStateFlow<List<LFolder>>(emptyList())

    fun seed(vararg folders: LFolder) {
        store.value = folders.toList()
    }

    fun clear() {
        store.value = emptyList()
    }

    override fun getFolders(): Flow<List<LFolder>> = store

    override fun getFolder(id: String): Flow<LFolder?> =
        store.mapLatest { list -> list.firstOrNull { it.id == id } }
}

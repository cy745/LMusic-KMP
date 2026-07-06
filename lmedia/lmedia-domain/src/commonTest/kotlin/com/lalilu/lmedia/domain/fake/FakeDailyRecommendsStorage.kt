@file:OptIn(ExperimentalCoroutinesApi::class)

package com.lalilu.lmedia.domain.fake

import com.lalilu.lmedia.domain.repository.DailyRecommendsStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeDailyRecommendsStorage : DailyRecommendsStorage {
    private val store = MutableStateFlow<List<String>>(emptyList())

    override fun flow(): Flow<List<String>> = store

    override suspend fun set(ids: List<String>) {
        store.value = ids
    }
}

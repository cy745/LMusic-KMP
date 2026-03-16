package com.lalilu.lmedia.data

import com.lalilu.lmedia.PlatformMediaSource
import com.lalilu.lmedia.entity.LItem
import com.lalilu.lmedia.source.MediaSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlin.reflect.KClass


@OptIn(ExperimentalCoroutinesApi::class)
abstract class Library {
    abstract fun platformMediaSource(): PlatformMediaSource
    abstract fun <T : LItem> getSourcesFlowByClass(clazz: KClass<T>): Flow<Map<String, T>>?
    abstract fun <T : LItem> getSourceFlowByClass(clazz: KClass<T>, id: String): Flow<T?>?

    fun requireMediaSource(sourceName: String): MediaSource {
        return platformMediaSource().sources
            .firstOrNull { sourceName == it.name }
            ?: throw Exception("No source item found for $sourceName")
    }

    suspend inline fun <reified T : LItem> get(id: String): T? =
        getSourceFlowByClass(T::class, id)
            ?.firstOrNull()

    suspend inline fun <reified T : LItem> get(): List<T> =
        getSourcesFlowByClass(T::class)
            ?.firstOrNull()?.values?.toList()
            ?: emptyList()

    suspend inline fun <reified T : LItem> mapBy(ids: List<String>): List<T> =
        getSourcesFlowByClass(T::class)
            ?.firstOrNull()?.let { map -> ids.mapNotNull { map[it] } }
            ?: emptyList()

    inline fun <reified T : LItem> flow(id: String): Flow<T?> =
        getSourceFlowByClass(T::class, id)
            ?: flowOf(null)

    inline fun <reified T : LItem> flow(): Flow<List<T>> =
        getSourcesFlowByClass(T::class)
            ?.mapLatest { it.values.toList() }
            ?: flowOf(emptyList())
}
package com.lalilu.lmedia.data

import com.lalilu.common.ext.io
import com.lalilu.lmedia.PlatformMediaSource
import com.lalilu.lmedia.entity.*
import com.lalilu.lmedia.source.MediaSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlin.reflect.KClass


@OptIn(ExperimentalCoroutinesApi::class)
abstract class Library {
    abstract fun platformMediaSource(): PlatformMediaSource
    abstract fun <T : LItem> getSourcesFlowByClass(clazz: KClass<T>): Flow<Map<String, T>>?
    abstract fun <T : LItem> getSourcesFlowByClass(clazz: KClass<T>, ids: List<String>): Flow<List<T>>?
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
        getSourcesFlowByClass(T::class, ids = ids)
            ?.firstOrNull()
            ?: emptyList()

    inline fun <reified T : LItem> mapByFlow(ids: List<String>): Flow<List<T>> =
        getSourcesFlowByClass(T::class, ids)
            ?: flowOf(emptyList())

    inline fun <reified T : LItem> flow(id: String): Flow<T?> =
        getSourceFlowByClass(T::class, id)
            ?: flowOf(null)

    inline fun <reified T : LItem> flow(): Flow<List<T>> =
        getSourcesFlowByClass(T::class)
            ?.mapLatest { it.values.toList() }
            ?: flowOf(emptyList())

    suspend fun getByPrefix(mediaId: String): LItem? {
        return when {
            mediaId.startsWith(LArtist.ID_PREFIX) -> get<LArtist>(mediaId)
            mediaId.startsWith(LAlbum.ID_PREFIX) -> get<LAlbum>(mediaId)
            mediaId.startsWith(LGenre.ID_PREFIX) -> get<LGenre>(mediaId)
            mediaId.startsWith(LFolder.ID_PREFIX) -> get<LFolder>(mediaId)
            mediaId.startsWith(LAudio.ID_PREFIX) -> get<LAudio>(mediaId)
            else -> get<LAudio>(mediaId)
        }
    }

    suspend fun mapByByPrefix(ids: List<String>): List<LItem> = withContext(Dispatchers.io) {
        val audios = ids.filter { it.startsWith(LAudio.ID_PREFIX) }
        val artists = ids.filter { it.startsWith(LArtist.ID_PREFIX) }
        val albums = ids.filter { it.startsWith(LAlbum.ID_PREFIX) }
        val folders = ids.filter { it.startsWith(LFolder.ID_PREFIX) }
        val genres = ids.filter { it.startsWith(LGenre.ID_PREFIX) }

        val jobs = mutableListOf<Deferred<Map<String, LItem>>>()
        if (audios.isNotEmpty()) jobs += async { mapBy<LAudio>(audios).associateBy { it.id } }
        if (artists.isNotEmpty()) jobs += async { mapBy<LArtist>(artists).associateBy { it.id } }
        if (albums.isNotEmpty()) jobs += async { mapBy<LAlbum>(albums).associateBy { it.id } }
        if (folders.isNotEmpty()) jobs += async { mapBy<LFolder>(folders).associateBy { it.id } }
        if (genres.isNotEmpty()) jobs += async { mapBy<LGenre>(genres).associateBy { it.id } }

        val fullMap = jobs.awaitAll()
            .fold(mapOf<String, LItem>()) { acc, map -> acc + map }

        ids.mapNotNull { mediaId -> fullMap[mediaId] }
    }
}
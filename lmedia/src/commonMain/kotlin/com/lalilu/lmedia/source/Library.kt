package com.lalilu.lmedia.source

import com.lalilu.lmedia.entity.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.reflect.KClass

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("UNCHECKED_CAST")
abstract class Library {
    val coroutineScope = CoroutineScope(Dispatchers.Default) + SupervisorJob()

    protected abstract fun snapshotFlow(): Flow<Snapshot>

    /**
     * 缓存数据源获取到的数据
     */
    val snapshotStateFlow by lazy {
        snapshotFlow()
            .stateIn(coroutineScope, SharingStarted.Lazily, Snapshot.Empty)
    }

    private fun <T : LItem> singleStateFlow(
        func: Snapshot.() -> List<T>
    ): StateFlow<Map<String, T>> {
        return snapshotStateFlow
            .map { it.func().associateBy(LItem::id) }
            .stateIn(coroutineScope, SharingStarted.Lazily, emptyMap())
    }

    private val _songsFlow by lazy { singleStateFlow(Snapshot::audios) }
    private val _albumsFlow by lazy { singleStateFlow(Snapshot::albums) }
    private val _artistsFlow by lazy { singleStateFlow(Snapshot::artists) }
    private val _genresFlow by lazy { singleStateFlow(Snapshot::genres) }
    private val _foldersFlow by lazy { singleStateFlow(Snapshot::folders) }

    fun <T : LItem> getSourceFlowByClass(clazz: KClass<T>): StateFlow<Map<String, T>>? {
        return when (clazz) {
            LAudio::class -> _songsFlow
            LArtist::class -> _artistsFlow
            LAlbum::class -> _albumsFlow
            LGenre::class -> _genresFlow
            LFolder::class -> _foldersFlow
            else -> null
        } as StateFlow<Map<String, T>>?
    }

    fun <T : LItem> getResultFlowByClass(clazz: KClass<T>): Flow<Map<String, T>> =
        getSourceFlowByClass(clazz) ?: flowOf(emptyMap())

    inline fun <reified T : LItem> get(id: String?): T? =
        getSourceFlowByClass(T::class)
            ?.value?.let { it[id] }

    inline fun <reified T : LItem> get(): List<T> =
        getSourceFlowByClass(T::class)
            ?.value?.values?.toList()
            ?: emptyList()

    inline fun <reified T : LItem> getFlow(id: String?): SharedFlow<T?> =
        getResultFlowByClass(T::class)
            .mapLatest { it[id] }
            .shareIn(coroutineScope, SharingStarted.Eagerly, 1)

    inline fun <reified T : LItem> getFlow(): SharedFlow<List<T>> =
        getResultFlowByClass(T::class)
            .mapLatest { it.values.toList() }
            .shareIn(coroutineScope, SharingStarted.Eagerly, 1)

    inline fun <reified T : LItem> mapBy(ids: List<String>): List<T> =
        getSourceFlowByClass(T::class)
            ?.value?.let { map -> ids.mapNotNull { map[it] } }
            ?: emptyList()

    inline fun <reified T : LItem> flowMapBy(ids: List<String>): Flow<List<T>> =
        getResultFlowByClass(T::class)
            .mapLatest { map -> ids.mapNotNull { map[it] } }

}
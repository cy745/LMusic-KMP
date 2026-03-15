package com.lalilu.lmedia.source

import com.lalilu.common.ext.ReadyState
import com.lalilu.common.ext.io
import com.lalilu.common.ext.readyStateImpl
import com.lalilu.lmedia.entity.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.reflect.KClass


@Deprecated("不在使用该Library实现，替换使用lmedia-data中处理这些数据")
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("UNCHECKED_CAST")
abstract class Library : ReadyState by readyStateImpl() {
    val coroutineScope = CoroutineScope(Dispatchers.io) + SupervisorJob()

    /**
     * 缓存数据源获取到的数据
     */
    abstract val snapshotStateFlow: StateFlow<Snapshot>

    protected fun <T : LItem> singleStateFlow(
        func: Snapshot.() -> List<T>
    ): StateFlow<Map<String, T>> {
        return snapshotStateFlow
            .map { it.func().associateBy(LItem::idValue) }
            .stateIn(coroutineScope, SharingStarted.Eagerly, emptyMap())
    }

    abstract fun <T : LItem> getSourceFlowByClass(clazz: KClass<T>): StateFlow<Map<String, T>>?

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
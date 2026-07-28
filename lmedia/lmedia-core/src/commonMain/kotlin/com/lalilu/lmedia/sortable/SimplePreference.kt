/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lalilu.lmedia.sortable

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.lalilu.lmedia.LMediaKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.mp.KoinPlatform

interface SimplePreference<T> {
    fun get(): T? = null
    fun set(value: T) {}
}

class SortActionPreference(
    prefix: String,
    private val defaultAction: SortAction? = null
) : SimplePreference<SortAction> {
    private val kv: LMediaKV by KoinPlatform.getKoin().inject<LMediaKV>()
    private val spItem = kv.obtain<String>("${prefix}sort_action")

    override fun get(): SortAction? = runCatching {
        spItem.value.let { KoinPlatform.getKoin().getOrNull<SortAction>(named(it)) }
            ?: defaultAction
    }.getOrNull()

    override fun set(value: SortAction) {
        spItem.value = value.key() ?: ""
    }
}

class SortConfigPreference(
    prefix: String,
    private val defaultConfig: SortConfig? = null
) : SimplePreference<SortConfig> {
    private val kv: LMediaKV by KoinPlatform.getKoin().inject<LMediaKV>()
    private val json: Json by KoinPlatform.getKoin().inject<Json>()
    private val spItem = kv.obtain<String>("${prefix}sort_config")

    override fun get(): SortConfig? = spItem.value
        .runCatching { json.decodeFromString<SortConfig>(this) }
        .getOrNull()
        ?: defaultConfig

    override fun set(value: SortConfig) {
        spItem.value = json.encodeToString(value)
    }
}

class SortManager(
    val prefix: String = "sort_manager_",
    val supportedActions: Collection<SortAction>,
    val defaultAction: SortAction? = supportedActions.firstOrNull(),
    val defaultConfig: SortConfig? = SortConfig(),
    private val sortActionPf: SortActionPreference = SortActionPreference(prefix, defaultAction),
    private val sortConfigPf: SortConfigPreference = SortConfigPreference(prefix, defaultConfig),
) {
    val sortConfig = MutableStateFlow(
        sortConfigPf.get()
            ?: defaultConfig
            ?: SortConfig()
    )
    val selectedAction = MutableStateFlow(
        sortActionPf.get()
            ?.takeIf { it in supportedActions }
            ?: defaultAction
            ?: supportedActions.firstOrNull()
    )

    suspend fun setConfig(config: SortConfig) {
        sortConfig.emit(config)
        sortConfigPf.set(config)
    }

    suspend fun setAction(action: SortAction) {
        if (action in supportedActions) {
            selectedAction.emit(action)
            sortActionPf.set(action)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<List<T>>.doSort(
    sortManager: SortManager,
): Flow<SortResult<T>> = sortManager.selectedAction.flatMapLatest { action ->
    sortManager.sortConfig.flatMapLatest { config ->
        action?.doSort(items = this@doSort, config)
            ?: this@doSort.mapLatest { SortResult.flat(it) }
    }
}

inline fun <reified T> Flow<List<T>>.doSortState(
    sortManager: SortManager,
    coroutineScope: CoroutineScope,
): State<SortResult<T>> = mutableStateOf(SortResult.empty<T>())
    .also { state ->
        this@doSortState.doSort(sortManager)
            .onEach { state.value = it }
            .launchIn(coroutineScope)
    }
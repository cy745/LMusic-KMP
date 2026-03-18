/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
fun <T : Sortable> Flow<List<T>>.doSort(
    sortManager: SortManager,
): Flow<SortResult<T>> = sortManager.selectedAction.flatMapLatest { action ->
    sortManager.sortConfig.flatMapLatest { config ->
        action?.doSort(items = this@doSort, config)
            ?: this@doSort.mapLatest { SortResult.Flat(it) }
    }
}

inline fun <reified T : Sortable> Flow<List<T>>.doSortState(
    sortManager: SortManager,
    coroutineScope: CoroutineScope,
): State<SortResult<T>> = mutableStateOf(SortResult.empty<T>())
    .also { state ->
        this@doSortState.doSort(sortManager)
            .onEach { state.value = it }
            .launchIn(coroutineScope)
    }
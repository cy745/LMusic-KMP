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

package com.lalilu.lhistory

import androidx.compose.runtime.Composable
import com.lalilu.lhistory.lhistory.generated.resources.Res
import com.lalilu.lhistory.lhistory.generated.resources.sort_preset_by_last_play_time
import com.lalilu.lhistory.lhistory.generated.resources.sort_preset_by_played_times
import com.lalilu.lhistory.repository.HistoryRepository
import com.lalilu.lmedia.sortable.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.jetbrains.compose.resources.stringResource
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single


@Named("sort_rule_play_count")
@Single(binds = [SortAction::class])
class SortRulePlayCount(
    private val historyRepo: HistoryRepository
) : SortAction {
    override fun key(): String = "sort_rule_play_count"

    @Composable
    override fun getActionInfo(): ActionInfo = ActionInfo(
        title = stringResource(Res.string.sort_preset_by_played_times),
        subTitle = "历史记录中播放次数排序"
    )

    override fun <T> doSort(
        items: Flow<List<T>>,
        config: SortConfig,
    ): Flow<SortResult<T>> {
        return historyRepo
            .getHistoriesIdsMapWithCount()
            .combine(items) { map, sources ->
                val sorted = sources
                    .sortedByDescending { song -> map[(song as? Sortable ?: song!!.toFallbackSortable()).getValueBy(Sortable.COMPARE_KEY_ID)] }
                    .let { if (config.reverse) it.reversed() else it }

                if (config.hideItemExtra) {
                    SortResult.flat(sorted)
                } else {
                    val extras = sorted.map {
                        ItemExtraData.PlayedCount(
                            count = map[(it as? Sortable ?: it!!.toFallbackSortable()).getValueBy(Sortable.COMPARE_KEY_ID)] ?: 0
                        )
                    }

                    SortResult(
                        groups = listOf(
                            SortedGroup(
                                groupId = null,
                                extras = extras,
                                items = sorted
                            )
                        )
                    )
                }
            }
    }
}

@Named("sort_rule_last_play_time")
@Single(binds = [SortAction::class])
class SortRuleLastPlayTime(
    private val historyRepo: HistoryRepository
) : SortAction {
    override fun key(): String = "sort_rule_last_play_time"

    @Composable
    override fun getActionInfo(): ActionInfo = ActionInfo(
        title = stringResource(Res.string.sort_preset_by_last_play_time),
        subTitle = "历史记录播放排序"
    )

    override fun <T> doSort(
        items: Flow<List<T>>,
        config: SortConfig,
    ): Flow<SortResult<T>> {
        return historyRepo
            .getHistoriesIdsMapWithLastTime()
            .combine(items) { map, sources ->
                val sorted = sources
                    .sortedByDescending { song -> map[(song as? Sortable ?: song!!.toFallbackSortable()).getValueBy(Sortable.COMPARE_KEY_ID)] }
                    .let { if (config.reverse) it.reversed() else it }

                SortResult.flat(sorted)
            }
    }
}
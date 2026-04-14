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

    override fun <T : Sortable> doSort(
        items: Flow<List<T>>,
        config: SortConfig,
    ): Flow<SortResult<T>> {
        return historyRepo
            .getHistoriesIdsMapWithCount()
            .combine(items) { map, sources ->
                val sorted = sources
                    .sortedByDescending { song -> map[song.getValueBy(Sortable.COMPARE_KEY_ID)] }
                    .let { if (config.reverse) it.reversed() else it }

                if (config.hideItemExtra) {
                    SortResult.flat(sorted)
                } else {
                    val extras = sorted.map {
                        ItemExtraData.PlayedCount(
                            count = map[it.getValueBy(Sortable.COMPARE_KEY_ID)] ?: 0
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

    override fun <T : Sortable> doSort(
        items: Flow<List<T>>,
        config: SortConfig,
    ): Flow<SortResult<T>> {
        return historyRepo
            .getHistoriesIdsMapWithLastTime()
            .combine(items) { map, sources ->
                val sorted = sources
                    .sortedByDescending { song -> map[song.getValueBy(Sortable.COMPARE_KEY_ID)] }
                    .let { if (config.reverse) it.reversed() else it }

                SortResult.flat(sorted)
            }
    }
}
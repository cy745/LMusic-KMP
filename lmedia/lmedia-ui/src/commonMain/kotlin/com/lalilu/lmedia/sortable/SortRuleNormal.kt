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

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale
import com.lalilu.common.ext.PlatformCollator
import com.lalilu.lmedia.lmedia_ui.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


@Named("sort_rule_normal")
@Single(binds = [SortAction::class])
class SortRuleNormal : SortAction {
    override fun key(): String = "sort_rule_normal"

    @Composable
    override fun getActionInfo(): ActionInfo = ActionInfo(
        title = stringResource(Res.string.sort_preset_by_normal),
        subTitle = "根据歌曲添加时间"
    )
}

@OptIn(ExperimentalTime::class)
@Named("sort_rule_add_time")
@Single(binds = [SortAction::class])
class AddTime : SortAction {
    override fun key(): String = "sort_rule_add_time"

    private val timeStrJustNow: String? by lazy { StringUtils.getString(R.string.group_identity_time_just_now) }
    private val timeStrMinutesAgo: String? by lazy { StringUtils.getString(R.string.group_identity_time_minutes_ago) }
    private val timeStrHoursAgo: String? by lazy { StringUtils.getString(R.string.group_identity_time_hours_ago) }
    private val timeStrExactDay: String? by lazy { StringUtils.getString(R.string.group_identity_time_exact_day_pattern) }

    @Composable
    override fun getActionInfo(): ActionInfo = ActionInfo(
        title = stringResource(Res.string.sort_preset_by_add_time),
        subTitle = "会显示添加时间分组"
    )

    override fun <T : Sortable> doSortInternal(
        items: List<T>,
        config: SortConfig
    ): SortResult<T> {
        val now = Clock.System.now().toEpochMilliseconds()

        val sorted = items
            .sortedByDescending { (it.getValueBy(Sortable.COMPARE_KEY_CREATE_TIME) ?: -1L) }
            .let { if (config.reverse) it.asReversed() else it }

        val grouped = sorted
            .groupBy { item ->
                val time = (item.getValueBy(Sortable.COMPARE_KEY_CREATE_TIME) ?: -1L) * 1000
                when {
                    now - time < 300000 -> timeStrJustNow
                    now - time < 3600000 -> timeStrMinutesAgo?.format((now - time) / 60000)
                    now - time < 86400000 -> timeStrHoursAgo?.format((now - time) / 3600000)
                    else -> timeStrExactDay?.let { TimeUtils.millis2String(time, it) }
                }
            }

        return SortResult.Grouped(grouped.map { map ->
            SortedGroup(
                groupId = GroupId.Time(map.key ?: "#"),
                items = map.value
            )
        })
    }
}

@Named("sort_rule_title")
@Single(binds = [SortAction::class])
class Title : SortAction {
    override fun key(): String = "sort_rule_title"
    private val collator by lazy { PlatformCollator(localeTag = Locale.current.toLanguageTag()) }
    private val pinyinTransformMap = mutableMapOf<String, String>()

    @Composable
    override fun getActionInfo(): ActionInfo = ActionInfo(
        title = stringResource(Res.string.sort_preset_by_title),
        subTitle = "标题首字符排序"
    )

    override fun <T : Sortable> doSortInternal(
        items: List<T>,
        config: SortConfig
    ): SortResult<T> {
        val sorted = items.sortedWith { a, b ->
            var aText = a.getValueBy<String>(Sortable.COMPARE_KEY_TITLE) ?: return@sortedWith 0
            var bText = b.getValueBy<String>(Sortable.COMPARE_KEY_TITLE) ?: return@sortedWith 0

            if (aText.firstOrNull()?.category == CharCategory.OTHER_LETTER) {
//                aText = pinyinTransformMap.getOrPut(aText) {
//                    runCatching { PinyinUtils.getPinyinFirstLetter(aText.take(1)).uppercase() }
//                        .getOrNull()
//                        ?: aText
//                }
            }

            if (bText.firstOrNull()?.category == CharCategory.OTHER_LETTER) {
//                bText = pinyinTransformMap.getOrPut(bText) {
//                    runCatching { PinyinUtils.getPinyinFirstLetter(bText.take(1)).uppercase() }
//                        .getOrNull()
//                        ?: bText
//                }
            }

            collator.compare(aText, bText)
        }.let { if (config.reverse) it.asReversed() else it }

        val grouped = sorted.groupBy {
            val text = it.getValueBy<String>(Sortable.COMPARE_KEY_TITLE)
            val firstLetter = text?.firstOrNull()
            if (firstLetter?.category == CharCategory.OTHER_LETTER) {
                pinyinTransformMap[text] ?: ""
            } else {
                firstLetter?.uppercase() ?: ""
            }
        }

        return SortResult.Grouped(grouped.map { map ->
            SortedGroup(
                groupId = GroupId.FirstLetter(map.key),
                items = map.value,
            )
        })
    }
}

@Named("sort_rule_duration")
@Single(binds = [SortAction::class])
class Duration : SortAction {
    override fun key(): String = "sort_rule_duration"

    @Composable
    override fun getActionInfo(): ActionInfo = ActionInfo(
        title = stringResource(Res.string.sort_preset_by_song_duration),
        subTitle = "根据歌曲时长排序"
    )

    override fun <T : Sortable> doSortInternal(
        items: List<T>,
        config: SortConfig
    ): SortResult<T> {
        val sorted = items
            .sortedByDescending { it.getValueBy(Sortable.COMPARE_KEY_DURATION) ?: -1L }
            .let { if (config.reverse) it.asReversed() else it }

        return SortResult.Flat(sorted)
    }
}


@Named("sort_rule_shuffle")
@Single(binds = [SortAction::class])
class Shuffle : SortAction {
    override fun key(): String = "sort_rule_shuffle"

    @Composable
    override fun getActionInfo(): ActionInfo = ActionInfo(
        title = stringResource(Res.string.sort_preset_by_shuffle),
        subTitle = "每次进入都会打乱顺序"
    )

    override fun <T : Sortable> doSortInternal(
        items: List<T>,
        config: SortConfig
    ): SortResult<T> {
        val shuffled = items.shuffled()
            .let { if (config.reverse) it.asReversed() else it }

        return SortResult.Flat(shuffled)
    }
}

/**
 * 元素内歌曲数量排序
 */
@Named("sort_rule_items_count")
@Single(binds = [SortAction::class])
class ItemsCount : SortAction {
    override fun key(): String = "sort_rule_items_count"

    @Composable
    override fun getActionInfo(): ActionInfo = ActionInfo(
        title = stringResource(Res.string.sort_preset_by_item_count),
        subTitle = "根据歌曲数量排序"
    )

    override fun <T : Sortable> doSortInternal(
        items: List<T>,
        config: SortConfig
    ): SortResult<T> {
        val sorted = items
            .sortedByDescending { it.getValueBy(Sortable.COMPARE_KEY_ITEMS_COUNT) ?: 0L }
            .let { if (config.reverse) it.asReversed() else it }

        return SortResult.Flat(sorted)
    }
}


@Named("sort_rule_album")
@Single(binds = [SortAction::class])
class Album : SortAction {
    override fun key(): String = "sort_rule_album"

    @Composable
    override fun getActionInfo(): ActionInfo = ActionInfo(
        title = stringResource(Res.string.sort_preset_by_disk_and_track),
        subTitle = "专辑的原始顺序"
    )

    override fun <T : Sortable> doSortInternal(
        items: List<T>,
        config: SortConfig
    ): SortResult<T> {
        val grouped = items.groupBy {
            it.getValueBy<String>(Sortable.COMPARE_KEY_DISK_NUMBER)
                ?.toIntOrNull()
                ?: -1
        }

        return SortResult.Grouped(grouped.map { map ->
            val list = map.value.sortedBy {
                it.getValueBy<String>(Sortable.COMPARE_KEY_TRACK_NUMBER)
                    ?.toIntOrNull()
                    ?: 0
            }.let { if (config.reverse) it.asReversed() else it }

            val extras: List<ItemExtraData> = list
                .takeIf { !config.hideItemExtra }
                ?.map {
                    val trackNum = it.getValueBy<String>(Sortable.COMPARE_KEY_TRACK_NUMBER)
                        ?.toIntOrNull()
                        ?: 0
                    ItemExtraData.TrackNumber(trackNum)
                } ?: emptyList()

            SortedGroup(
                groupId = map.key.takeIf { it >= 0 }?.let { GroupId.DiskNumber(it) },
                extras = extras,
                items = list
            )
        })
    }
}
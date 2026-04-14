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

package com.lalilu.lplaylist.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.lalilu.lmedia.entity.TextMatchable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
@Entity(tableName = LPlaylist.TABLE_NAME)
data class LPlaylist(
    @PrimaryKey
    val id: String,
    val title: String,
    val subTitle: String,
    val coverUri: String,
    val mediaIds: List<String>,
    val createTime: Long = Clock.System.now().toEpochMilliseconds(),
    val modifyTime: Long = Clock.System.now().toEpochMilliseconds(),
) : TextMatchable {
    override fun getMatchText(): String = "$title$subTitle"

    companion object {
        const val TABLE_NAME = "l_playlist"
    }
}
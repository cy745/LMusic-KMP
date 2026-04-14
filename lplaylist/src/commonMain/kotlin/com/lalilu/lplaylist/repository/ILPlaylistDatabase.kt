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

package com.lalilu.lplaylist.repository

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.executeSQL
import com.lalilu.extensions.get
import com.lalilu.lplaylist.entity.LPlaylist
import com.lalilu.lplaylist.lplaylist.generated.resources.Res
import com.lalilu.lplaylist.lplaylist.generated.resources.playlist_tips_favourite
import com.lalilu.lplaylist.lplaylist.generated.resources.playlist_tips_favourite_subTitle
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
interface ILPlaylistDatabase {
    fun playlistDao(): LPlaylistDao

    companion object {
        val CALLBACK = object : RoomDatabase.Callback() {
            override suspend fun onOpen(connection: SQLiteConnection) {
                // 每次启动时尝试插入喜欢列表，如果存在则无操作
                val now = Clock.System.now().toEpochMilliseconds()
                val table = LPlaylist.TABLE_NAME
                val id = PlaylistRepository.FAVOURITE_PLAYLIST_ID
                val title = Res.string.playlist_tips_favourite.get()
                val subtitle = Res.string.playlist_tips_favourite_subTitle.get()
                val sql = "INSERT OR IGNORE INTO $table VALUES('$id','$title','$subtitle','','[]',$now,$now);"
                connection.executeSQL(sql)
            }
        }
    }
}
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
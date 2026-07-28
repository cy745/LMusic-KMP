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

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.lalilu.lplaylist.entity.LPlaylist
import kotlinx.coroutines.flow.Flow

@Dao
interface LPlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(playlists: List<LPlaylist>)

    @Query("DELETE FROM ${LPlaylist.TABLE_NAME}")
    suspend fun removeAll()

    @Query("SELECT * FROM ${LPlaylist.TABLE_NAME}")
    suspend fun getAll(): List<LPlaylist>

    @Query("SELECT * FROM ${LPlaylist.TABLE_NAME}")
    fun getAllFlow(): Flow<List<LPlaylist>>

    @Transaction
    suspend fun updatePlaylists(playlists: List<LPlaylist>) {
        removeAll()
        insertAll(playlists)
    }
}
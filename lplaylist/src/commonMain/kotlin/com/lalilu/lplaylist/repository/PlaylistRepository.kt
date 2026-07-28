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

import com.lalilu.lplaylist.entity.LPlaylist
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    companion object {
        const val FAVOURITE_PLAYLIST_ID = "FAVOURITE"
    }

    fun getPlaylistsFlow(): Flow<List<LPlaylist>>
    suspend fun getPlaylists(): List<LPlaylist>
    suspend fun setPlaylists(playlists: List<LPlaylist>)

    suspend fun save(playlist: LPlaylist)
    suspend fun remove(playlist: LPlaylist)
    suspend fun removeById(id: String)
    suspend fun removeByIds(ids: List<String>)
    suspend fun isExist(playlistId: String): Boolean
    suspend fun isExistInPlaylist(playlistId: String, mediaId: String): Boolean

    suspend fun updateMediaIdsToPlaylist(mediaIds: List<String>, playlistId: String)
    suspend fun addMediaIdsToPlaylist(mediaIds: List<String>, playlistId: String)
    suspend fun addMediaIdsToPlaylists(mediaIds: List<String>, playlistIds: List<String>)
    suspend fun removeMediaIdsFromPlaylist(mediaIds: List<String>, playlistId: String)
    suspend fun removeMediaIdsFromPlaylists(mediaIds: List<String>, playlistIds: List<String>)

    suspend fun updateMediaIdsToFavourite(mediaIds: List<String>)
    suspend fun addMediaIdsToFavourite(mediaIds: List<String>)
    suspend fun removeMediaIdsFromFavourite(mediaIds: List<String>)

    /**
     * 检查我喜欢歌单是否存在，若不存在则创建
     */
    suspend fun checkFavouriteExist(): Boolean
    fun getFavouriteMediaIds(): Flow<List<String>>
    fun isItemInFavourite(mediaId: String): Flow<Boolean>
}
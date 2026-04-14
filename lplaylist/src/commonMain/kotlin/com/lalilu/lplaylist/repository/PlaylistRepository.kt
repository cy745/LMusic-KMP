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
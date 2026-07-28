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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import org.koin.core.annotation.Single

@OptIn(ExperimentalCoroutinesApi::class)
@Single(binds = [PlaylistRepository::class])
internal class PlaylistRepositoryImpl(
    private val database: ILPlaylistDatabase
) : PlaylistRepository {
    private val playlistDao by lazy { database.playlistDao() }

    override fun getPlaylistsFlow(): Flow<List<LPlaylist>> {
        return playlistDao.getAllFlow().mapLatest { it.distinctBy { it.id } }
    }

    override suspend fun getPlaylists(): List<LPlaylist> {
        return playlistDao.getAll()
    }

    override suspend fun setPlaylists(playlists: List<LPlaylist>) {
        playlistDao.updatePlaylists(playlists.distinctBy { it.id })
    }

    override suspend fun save(playlist: LPlaylist) {
        val playlists = getPlaylists().toMutableList()
        val index = playlists.indexOfFirst { it.id == playlist.id }

        // 若已存在则更新
        if (index >= 0) {
            playlists[index] = playlist
            setPlaylists(playlists)
            return
        }

        playlists.add(0, playlist)
        setPlaylists(playlists)
    }

    override suspend fun remove(playlist: LPlaylist) {
        val playlists = getPlaylists().toMutableList()
        playlists.remove(playlist)
        setPlaylists(playlists)
    }

    override suspend fun removeById(id: String) {
        if (id == PlaylistRepository.FAVOURITE_PLAYLIST_ID) {
//            ToastUtils.showShort(R.string.playlist_tips_cannot_remove_favourite)
            return
        }

        // 筛选不用删除的元素
        val result = getPlaylists().filter { it.id != id }
        setPlaylists(result)
    }

    override suspend fun removeByIds(ids: List<String>) {
        if (ids.contains(PlaylistRepository.FAVOURITE_PLAYLIST_ID)) {
//            ToastUtils.showShort(R.string.playlist_tips_cannot_remove_favourite)
        }

        // 筛选不用删除的元素
        val result = getPlaylists().filter {
            !ids.contains(it.id) || it.id == PlaylistRepository.FAVOURITE_PLAYLIST_ID
        }
        setPlaylists(result)
    }

    override suspend fun isExist(playlistId: String): Boolean {
        return getPlaylists().any { it.id == playlistId }
    }

    override suspend fun isExistInPlaylist(playlistId: String, mediaId: String): Boolean {
        val playlists = getPlaylists()
        val playlist = playlists.firstOrNull { it.id == playlistId } ?: return false
        return playlist.mediaIds.contains(mediaId)
    }

    override suspend fun updateMediaIdsToPlaylist(mediaIds: List<String>, playlistId: String) {
        updatePlaylist(playlistId) { it.copy(mediaIds = mediaIds.distinct()) }
    }

    override suspend fun addMediaIdsToPlaylist(mediaIds: List<String>, playlistId: String) {
        updatePlaylist(playlistId) { it.copy(mediaIds = mediaIds.plus(it.mediaIds).distinct()) }
    }

    override suspend fun addMediaIdsToPlaylists(mediaIds: List<String>, playlistIds: List<String>) {
        var changed = false
        val playlists = getPlaylists().toMutableList()

        for (index in playlists.indices) {
            val playlist = playlists[index]
            val playlistId = playlist.id
            val exist = playlistIds.any { it == playlistId }
            if (!exist) continue

            val mediaIdsSet = playlist.mediaIds.toHashSet()
                .also {
                    changed = true
                    it.addAll(mediaIds)
                }

            playlists[index] = playlist.copy(mediaIds = mediaIdsSet.toList())
        }

        if (!changed) return
        setPlaylists(playlists)
    }

    override suspend fun removeMediaIdsFromPlaylist(mediaIds: List<String>, playlistId: String) {
        updatePlaylist(playlistId) { it.copy(mediaIds = it.mediaIds.minus(mediaIds.toSet())) }
    }

    override suspend fun removeMediaIdsFromPlaylists(mediaIds: List<String>, playlistIds: List<String>) {
        var changed = false
        val playlists = getPlaylists().toMutableList()

        for (index in playlists.indices) {
            val playlist = playlists[index]
            val playlistId = playlist.id
            val exist = playlistIds.any { it == playlistId }
            if (!exist) continue

            changed = true
            val newMediaIds = playlist.mediaIds.minus(mediaIds.toSet())

            playlists[index] = playlist.copy(mediaIds = newMediaIds)
        }

        if (!changed) return
        setPlaylists(playlists)
    }

    override suspend fun updateMediaIdsToFavourite(mediaIds: List<String>) {
        updateMediaIdsToPlaylist(mediaIds, PlaylistRepository.FAVOURITE_PLAYLIST_ID)
    }

    override suspend fun addMediaIdsToFavourite(mediaIds: List<String>) {
        if (checkFavouriteExist()) {
            addMediaIdsToPlaylist(mediaIds, PlaylistRepository.FAVOURITE_PLAYLIST_ID)
        }
    }

    override suspend fun removeMediaIdsFromFavourite(mediaIds: List<String>) {
        if (checkFavouriteExist()) {
            removeMediaIdsFromPlaylist(mediaIds, PlaylistRepository.FAVOURITE_PLAYLIST_ID)
        }
    }

    override suspend fun checkFavouriteExist(): Boolean {
        val playlists = getPlaylists()
        val exist = playlists.any { it.id == PlaylistRepository.FAVOURITE_PLAYLIST_ID }

        if (!exist) {
            save(
                LPlaylist(
                    id = PlaylistRepository.FAVOURITE_PLAYLIST_ID,
                    title = "我喜欢", // context.getString(R.string.playlist_tips_favourite),
                    subTitle = "我喜欢的歌曲", // context.getString(R.string.playlist_tips_favourite_subTitle),
                    coverUri = "",
                    mediaIds = emptyList()
                )
            )
        }

        return exist
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getFavouriteMediaIds(): Flow<List<String>> {
        return getPlaylistsFlow()
            .mapLatest { playlists ->
                playlists
                    .firstOrNull { it.id == PlaylistRepository.FAVOURITE_PLAYLIST_ID }
                    ?.mediaIds ?: emptyList()
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun isItemInFavourite(mediaId: String): Flow<Boolean> {
        return getFavouriteMediaIds()
            .mapLatest { it.contains(mediaId) }
    }

    private suspend fun updatePlaylist(playlistId: String, action: (LPlaylist) -> LPlaylist) {
        val playlists = getPlaylists().toMutableList()
        val index = playlists.indexOfFirst { it.id == playlistId }.takeIf { it >= 0 } ?: return

        playlists[index] = action(playlists[index])
        setPlaylists(playlists)
    }
}
package com.lalilu.lplaylist.repository

import com.lalilu.lplaylist.entity.LPlaylist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class FavouriteRepositoryTest {
    @Test fun firstFavouriteIsSavedAndSourceIdentitySurvivesToggle() = runTest {
        val rows = MutableStateFlow<List<LPlaylist>>(emptyList())
        val dao = object : LPlaylistDao {
            override suspend fun insertAll(playlists: List<LPlaylist>) { rows.value = playlists }
            override suspend fun removeAll() { rows.value = emptyList() }
            override suspend fun getAll() = rows.value
            override fun getAllFlow() = rows
        }
        val repository = PlaylistRepositoryImpl(object : ILPlaylistDatabase {
            override fun playlistDao() = dao
        })
        val local = "5:localaudio_same"
        val remote = "6:remoteaudio_same"
        repository.addMediaIdsToFavourite(listOf(local))
        assertTrue(repository.isItemInFavourite(local).first())
        assertFalse(repository.isItemInFavourite(remote).first())
        repository.addMediaIdsToFavourite(listOf(local, remote))
        assertEquals(listOf(local, remote), repository.getFavouriteMediaIds().first())
        repository.removeMediaIdsFromFavourite(listOf(local))
        assertFalse(repository.isItemInFavourite(local).first())
        assertTrue(repository.isItemInFavourite(remote).first())
    }
}

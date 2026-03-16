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

package com.lalilu.lmedia.data.database

import androidx.room3.*
import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXAlbum
import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXGenre
import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXLArtist
import com.lalilu.lmedia.entity.*

@Dao
interface LMediaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudio(list: List<LAudio>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtist(list: List<LArtist>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(list: List<LAlbum>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenre(list: List<LGenre>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtistRelation(list: List<CrossRefLAudioXLArtist>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbumRelation(list: List<CrossRefLAudioXAlbum>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenreRelation(list: List<CrossRefLAudioXGenre>)

    @Query("SELECT * FROM l_audio WHERE media_source_name = :source")
    suspend fun getAudioBySource(source: String): List<LAudio>

    @Transaction
    suspend fun insert(snapshot: Snapshot, sourceName: String) {
        val audioFromSource = getAudioBySource(sourceName)
        val audioMap = snapshot.audios.associateBy { it.idValue() }

        val audioToUpdate = audioFromSource
            .filter { audio -> audioMap[audio.idValue()] == null }
            .map { it.copy(available = false) }

        // 插入所有实体
        insertAudio(list = snapshot.audios + audioToUpdate)
        insertArtist(list = snapshot.artists)
        insertAlbum(list = snapshot.albums)
        insertGenre(list = snapshot.genres)

        // 构建并插入关联关系
        val artistRelations = snapshot.audios.flatMap { song ->
            val artists = song.ref<LArtist>()
            artists.map { artist ->
                CrossRefLAudioXLArtist(
                    songId = song.idValue(),
                    artistId = artist.idValue()
                )
            }
        }

        val albumRelations = snapshot.audios.flatMap { song ->
            val albums = song.ref<LAlbum>()
            albums.map { album ->
                CrossRefLAudioXAlbum(
                    songId = song.idValue(),
                    albumId = album.idValue()
                )
            }
        }

        val genreRelations = snapshot.audios.flatMap { song ->
            val genres = song.ref<LGenre>()
            genres.map { genre ->
                CrossRefLAudioXGenre(
                    songId = song.idValue(),
                    genreId = genre.idValue()
                )
            }
        }

        insertArtistRelation(list = artistRelations)
        insertAlbumRelation(list = albumRelations)
        insertGenreRelation(list = genreRelations)
    }
}
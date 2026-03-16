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

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Transaction
import com.lalilu.lmedia.entity.LAlbum
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LGenre
import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.entity.ref
import com.lalilu.lmedia.entity.relation.CrossRefLAudioXLArtist
import com.lalilu.lmedia.entity.relation.CrossRefLAudioXAlbum
import com.lalilu.lmedia.entity.relation.CrossRefLAudioXGenre

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

    @Transaction
    suspend fun insert(snapshot: Snapshot) {
        // 插入所有实体
        insertAudio(list = snapshot.audios)
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
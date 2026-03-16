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

package com.lalilu.lmedia.data.database.relation

import androidx.room3.Embedded
import androidx.room3.Junction
import androidx.room3.Relation
import com.lalilu.lmedia.entity.LAlbum
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LGenre

/**
 * LArtist 的完整关联查询结果
 * 包含 LArtist 本身及其关联的 Audio
 */
data class QueryLArtistWithAudios(
    @Embedded val artist: LArtist,

    @Relation(
        parentColumn = "artist_id",
        entityColumn = "song_id",
        associateBy = Junction(CrossRefLAudioXLArtist::class)
    )
    val audios: List<LAudio>
)

/**
 * LAlbum 的完整关联查询结果
 * 包含 LAlbum 本身及其关联的 Audio
 */
data class QueryLAlbumWithAudios(
    @Embedded val album: LAlbum,

    @Relation(
        parentColumn = "album_id",
        entityColumn = "song_id",
        associateBy = Junction(CrossRefLAudioXAlbum::class)
    )
    val audios: List<LAudio>
)

/**
 * LGenre 的完整关联查询结果
 * 包含 LGenre 本身及其关联的 Audio
 */
data class QueryLGenreWithAudios(
    @Embedded val genre: LGenre,

    @Relation(
        parentColumn = "genre_id",
        entityColumn = "song_id",
        associateBy = Junction(CrossRefLAudioXGenre::class)
    )
    val audios: List<LAudio>
)

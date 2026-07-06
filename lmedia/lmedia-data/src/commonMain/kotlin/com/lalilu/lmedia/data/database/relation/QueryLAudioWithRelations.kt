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
import com.lalilu.lmedia.data.entity.LAlbumEntity
import com.lalilu.lmedia.data.entity.LArtistEntity
import com.lalilu.lmedia.data.entity.LAudioEntity
import com.lalilu.lmedia.data.entity.LGenreEntity

/**
 * LAudio 的完整关联查询结果
 * 包含 LAudio 本身及其关联的 Artist、Album、Genre
 */
data class QueryLAudioWithRelations(
    @Embedded val audio: LAudioEntity,

    @Relation(
        parentColumn = "song_id",
        entityColumn = "artist_id",
        associateBy = Junction(CrossRefLAudioXLArtist::class)
    )
    val artists: List<LArtistEntity>,

    @Relation(
        parentColumn = "song_id",
        entityColumn = "album_id",
        associateBy = Junction(CrossRefLAudioXAlbum::class)
    )
    val albums: List<LAlbumEntity>,

    @Relation(
        parentColumn = "song_id",
        entityColumn = "genre_id",
        associateBy = Junction(CrossRefLAudioXGenre::class)
    )
    val genres: List<LGenreEntity>
)

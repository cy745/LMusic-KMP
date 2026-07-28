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

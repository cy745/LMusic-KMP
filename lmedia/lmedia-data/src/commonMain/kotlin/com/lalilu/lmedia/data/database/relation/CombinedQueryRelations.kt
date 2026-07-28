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
 * LArtist 的完整关联查询结果
 * 包含 LArtist 本身及其关联的 Audio
 */
data class QueryLArtistWithAudios(
    @Embedded val artist: LArtistEntity,

    @Relation(
        parentColumn = "artist_id",
        entityColumn = "song_id",
        associateBy = Junction(CrossRefLAudioXLArtist::class)
    )
    val audios: List<LAudioEntity>
)

/**
 * LAlbum 的完整关联查询结果
 * 包含 LAlbum 本身及其关联的 Audio
 */
data class QueryLAlbumWithAudios(
    @Embedded val album: LAlbumEntity,

    @Relation(
        parentColumn = "album_id",
        entityColumn = "song_id",
        associateBy = Junction(CrossRefLAudioXAlbum::class)
    )
    val audios: List<LAudioEntity>
)

/**
 * LGenre 的完整关联查询结果
 * 包含 LGenre 本身及其关联的 Audio
 */
data class QueryLGenreWithAudios(
    @Embedded val genre: LGenreEntity,

    @Relation(
        parentColumn = "genre_id",
        entityColumn = "song_id",
        associateBy = Junction(CrossRefLAudioXGenre::class)
    )
    val audios: List<LAudioEntity>
)

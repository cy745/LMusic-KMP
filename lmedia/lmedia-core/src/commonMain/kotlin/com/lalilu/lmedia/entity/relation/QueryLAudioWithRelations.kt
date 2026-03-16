package com.lalilu.lmedia.entity.relation

import androidx.room3.Embedded
import androidx.room3.Junction
import androidx.room3.Relation
import com.lalilu.lmedia.entity.LAlbum
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LGenre

/**
 * LAudio 的完整关联查询结果
 * 包含 LAudio 本身及其关联的 Artist、Album、Genre
 */
data class QueryLAudioWithRelations(
    @Embedded val audio: LAudio,

    @Relation(
        parentColumn = "song_id",
        entityColumn = "artist_id",
        associateBy = Junction(CrossRefLAudioXLArtist::class)
    )
    val artists: List<LArtist>,

    @Relation(
        parentColumn = "song_id",
        entityColumn = "album_id",
        associateBy = Junction(CrossRefLAudioXAlbum::class)
    )
    val albums: List<LAlbum>,

    @Relation(
        parentColumn = "song_id",
        entityColumn = "genre_id",
        associateBy = Junction(CrossRefLAudioXGenre::class)
    )
    val genres: List<LGenre>
)

package com.lalilu.lmedia.entity.relation

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

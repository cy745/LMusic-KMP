package com.lalilu.lmedia.entity.relation

import androidx.room3.Embedded
import androidx.room3.Junction
import androidx.room3.Relation
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.LAudio

data class QueryLArtistWithLAudioList(
    @Embedded val artist: LArtist,
    @Relation(
        parentColumn = "artist_id",
        entityColumn = "song_id",
        associateBy = Junction(CrossRefLAudioXLArtist::class)
    )
    val audios: List<LAudio>
)
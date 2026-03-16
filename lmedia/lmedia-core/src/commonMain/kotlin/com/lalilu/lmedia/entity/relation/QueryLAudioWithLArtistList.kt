package com.lalilu.lmedia.entity.relation

import androidx.room3.Embedded
import androidx.room3.Junction
import androidx.room3.Relation
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.LAudio


data class QueryLAudioWithLArtistList(
    @Embedded val audio: LAudio,
    @Relation(
        parentColumn = "song_id",
        entityColumn = "artist_id",
        associateBy = Junction(CrossRefLAudioXLArtist::class)
    )
    val artist: List<LArtist>
)
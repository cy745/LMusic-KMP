package com.lalilu.lmedia.domain.repository

import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.flow.Flow

interface ArtistRepository {
    fun getArtists(): Flow<List<LArtist>>
    fun getArtists(ids: List<String>): Flow<List<LArtist>>
    fun getArtist(id: String): Flow<LArtist?>

    /** Returns audios associated with this artist via cross-ref table. */
    fun getAudiosByArtist(artistId: String): Flow<List<LAudio>>

    /** Returns IDs of audios associated with this artist via cross-ref table. */
    fun getAudioIdsByArtist(artistId: String): Flow<List<String>>

    /** Returns IDs of artists associated with the given audio IDs via cross-ref table. */
    fun getArtistIdsByAudioIds(audioIds: List<String>): Flow<List<String>>
}

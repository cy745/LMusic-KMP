package com.lalilu.lmedia.domain.repository

import com.lalilu.lmedia.domain.model.LArtist
import kotlinx.coroutines.flow.Flow

interface ArtistRepository {
    fun getArtists(): Flow<List<LArtist>>
    fun getArtists(ids: List<String>): Flow<List<LArtist>>
    fun getArtist(id: String): Flow<LArtist?>
}

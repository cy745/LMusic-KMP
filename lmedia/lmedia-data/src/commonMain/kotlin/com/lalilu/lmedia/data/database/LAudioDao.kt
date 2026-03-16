package com.lalilu.lmedia.data.database

import androidx.room3.*
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.link
import com.lalilu.lmedia.entity.relation.QueryLAudioWithLArtistList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

@OptIn(ExperimentalCoroutinesApi::class)
@Dao
interface LAudioDao {
    @Insert
    suspend fun insert(audio: LAudio)

    @Update
    suspend fun update(audio: LAudio)

    @Delete
    suspend fun delete(audio: LAudio)


    @Transaction
    @Query("SELECT * FROM l_audio")
    fun getAllAudioWithArtists(): Flow<List<QueryLAudioWithLArtistList>>

    fun getAllAudio(): Flow<List<LAudio>> = getAllAudioWithArtists().mapLatest { list ->
        list.map { it.audio.apply { it.artist.forEach { artist -> link(artist) } } }
    }

    @Transaction
    @Query("SELECT * FROM l_audio WHERE song_id = :id")
    fun getAudioWithArtists(id: String): Flow<QueryLAudioWithLArtistList>

    fun getAudio(id: String): Flow<LAudio> = getAudioWithArtists(id).mapLatest {
        it.audio.apply { it.artist.forEach { artist -> link(artist) } }
    }
}
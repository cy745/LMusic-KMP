package com.lalilu.lmedia.data.database

import androidx.room3.*
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.link
import com.lalilu.lmedia.entity.relation.CrossRefLAudioXLArtist
import com.lalilu.lmedia.entity.relation.QueryLAudioWithRelations
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
    fun getAllAudioWithRelations(): Flow<List<QueryLAudioWithRelations>>

    fun getAllAudio(): Flow<List<LAudio>> = getAllAudioWithRelations().mapLatest { list ->
        list.map { query ->
            query.audio.also { audio ->
                query.artists.forEach { artist -> audio.link(artist) }
                query.albums.forEach { album -> audio.link(album) }
                query.genres.forEach { genre -> audio.link(genre) }
            }
        }
    }

    @Transaction
    @Query("SELECT * FROM l_audio WHERE song_id = :id")
    fun getAudioWithRelations(id: String): Flow<QueryLAudioWithRelations>

    fun getAudio(id: String): Flow<LAudio> = getAudioWithRelations(id).mapLatest { query ->
        query.audio.also { audio ->
            query.artists.forEach { artist -> audio.link(artist) }
            query.albums.forEach { album -> audio.link(album) }
            query.genres.forEach { genre -> audio.link(genre) }
        }
    }
}

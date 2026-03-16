package com.lalilu.lmedia.data.database

import androidx.room3.*
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.link
import com.lalilu.lmedia.entity.relation.CrossRefLAudioXLArtist
import com.lalilu.lmedia.entity.relation.QueryLArtistWithAudios
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

@OptIn(ExperimentalCoroutinesApi::class)
@Dao
interface LArtistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(artist: LArtist)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(artists: List<LArtist>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelation(relations: List<CrossRefLAudioXLArtist>)

    @Update
    suspend fun update(artist: LArtist)

    @Delete
    suspend fun delete(artist: LArtist)

    @Transaction
    @Query("SELECT * FROM l_artist")
    fun getAllArtistWithAudios(): Flow<List<QueryLArtistWithAudios>>

    fun getAllArtist(): Flow<List<LArtist>> = getAllArtistWithAudios().mapLatest { list ->
        list.map { query ->
            query.artist.also { artist ->
                query.audios.forEach { audio ->
                    artist.link(audio)
                    audio.link(artist)
                }
            }
        }
    }

    @Transaction
    @Query("SELECT * FROM l_artist WHERE artist_id = :artistId")
    fun getArtistWithAudios(artistId: String): Flow<QueryLArtistWithAudios>

    fun getArtist(artistId: String): Flow<LArtist> = getArtistWithAudios(artistId)
        .mapLatest { query ->
            query.artist.also { artist ->
                query.audios.forEach { audio ->
                    artist.link(audio)
                    audio.link(artist)
                }
            }
        }

    @Transaction
    @Query("SELECT * FROM l_audio WHERE song_id IN (SELECT song_id FROM cross_ref_audio_x_artist WHERE artist_id = :artistId)")
    fun getAudiosByArtist(artistId: String): Flow<List<LAudio>>
}

package com.lalilu.lmedia.data.database

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.lalilu.lmedia.data.entity.LArtistEntity
import com.lalilu.lmedia.data.entity.LAudioEntity
import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXLArtist
import com.lalilu.lmedia.data.database.relation.QueryLArtistWithAudios
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

@OptIn(ExperimentalCoroutinesApi::class)
@Dao
interface LArtistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(artist: LArtistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(artists: List<LArtistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelation(relations: List<CrossRefLAudioXLArtist>)

    @Update
    suspend fun update(artist: LArtistEntity)

    @Delete
    suspend fun delete(artist: LArtistEntity)

    @Transaction
    @Query("SELECT * FROM l_artist")
    fun getAllArtistWithAudios(): Flow<List<QueryLArtistWithAudios>>

    fun getAllArtist(): Flow<List<LArtistEntity>> =
        getAllArtistWithAudios().mapLatest { list -> list.map { it.artist } }

    @Transaction
    @Query("SELECT * FROM l_artist WHERE artist_id = :artistId")
    fun getArtistWithAudios(artistId: String): Flow<QueryLArtistWithAudios?>

    fun getArtist(artistId: String): Flow<LArtistEntity?> =
        getArtistWithAudios(artistId).mapLatest { it?.artist }

    @Transaction
    @Query("SELECT * FROM l_artist WHERE artist_id IN (:artistIds)")
    fun getArtistsWithAudios(artistIds: List<String>): Flow<List<QueryLArtistWithAudios>>

    fun getArtists(artistIds: List<String>): Flow<List<LArtistEntity>> =
        getArtistsWithAudios(artistIds).mapLatest { list ->
            list.map { it.artist }
        }

    @Transaction
    @Query("SELECT * FROM l_audio WHERE song_id IN (SELECT song_id FROM cross_ref_audio_x_artist WHERE artist_id = :artistId)")
    fun getAudiosByArtist(artistId: String): Flow<List<LAudioEntity>>

    @Query("SELECT artist_id FROM cross_ref_audio_x_artist WHERE song_id IN (:audioIds)")
    fun getArtistIdsByAudioIds(audioIds: List<String>): Flow<List<String>>
}

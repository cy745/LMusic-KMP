package com.lalilu.lmedia.data.database

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.lalilu.lmedia.data.entity.LAlbumEntity
import com.lalilu.lmedia.data.entity.LAudioEntity
import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXAlbum
import com.lalilu.lmedia.data.database.relation.QueryLAlbumWithAudios
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

@OptIn(ExperimentalCoroutinesApi::class)
@Dao
interface LAlbumDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(album: LAlbumEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(albums: List<LAlbumEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelation(relations: List<CrossRefLAudioXAlbum>)

    @Update
    suspend fun update(album: LAlbumEntity)

    @Delete
    suspend fun delete(album: LAlbumEntity)

    @Transaction
    @Query("SELECT * FROM l_album")
    fun getAllAlbumWithAudios(): Flow<List<QueryLAlbumWithAudios>>

    fun getAllAlbum(): Flow<List<LAlbumEntity>> =
        getAllAlbumWithAudios().mapLatest { list -> list.map { it.album } }

    @Transaction
    @Query("SELECT * FROM l_album WHERE album_id = :id")
    fun getAlbumWithAudios(id: String): Flow<QueryLAlbumWithAudios?>

    fun getAlbum(id: String): Flow<LAlbumEntity?> =
        getAlbumWithAudios(id).mapLatest { it?.album }

    @Transaction
    @Query("SELECT * FROM l_album WHERE album_id IN (:ids)")
    fun getAlbumsWithAudios(ids: List<String>): Flow<List<QueryLAlbumWithAudios>>

    fun getAlbums(ids: List<String>): Flow<List<LAlbumEntity>> =
        getAlbumsWithAudios(ids).mapLatest { list ->
            list.map { it.album }
        }

    @Transaction
    @Query("SELECT * FROM l_audio WHERE song_id IN (SELECT song_id FROM cross_ref_audio_x_album WHERE album_id = :albumId)")
    fun getAudiosByAlbum(albumId: String): Flow<List<LAudioEntity>>
}

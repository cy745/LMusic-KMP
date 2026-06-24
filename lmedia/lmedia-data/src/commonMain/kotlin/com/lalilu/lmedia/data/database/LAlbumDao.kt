package com.lalilu.lmedia.data.database

import androidx.room3.*
import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXAlbum
import com.lalilu.lmedia.data.database.relation.QueryLAlbumWithAudios
import com.lalilu.lmedia.entity.LAlbum
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.link
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

@OptIn(ExperimentalCoroutinesApi::class)
@Dao
interface LAlbumDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(album: LAlbum)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(albums: List<LAlbum>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelation(relations: List<CrossRefLAudioXAlbum>)

    @Update
    suspend fun update(album: LAlbum)

    @Delete
    suspend fun delete(album: LAlbum)

    @Transaction
    @Query("SELECT * FROM l_album")
    fun getAllAlbumWithAudios(): Flow<List<QueryLAlbumWithAudios>>

    fun getAllAlbum(): Flow<List<LAlbum>> = getAllAlbumWithAudios().mapLatest { list ->
        list.map { query ->
            query.album.also { album ->
                query.audios.forEach { audio ->
                    album.link(audio)
                    audio.link(album)
                }
            }
        }
    }

    @Transaction
    @Query("SELECT * FROM l_album WHERE album_id = :id")
    fun getAlbumWithAudios(id: String): Flow<QueryLAlbumWithAudios?>

    @Transaction
    @Query("SELECT * FROM l_album WHERE album_id IN (:ids)")
    fun getAlbumsWithAudios(ids: List<String>): Flow<List<QueryLAlbumWithAudios>>

    fun getAlbum(id: String): Flow<LAlbum?> = getAlbumWithAudios(id).mapLatest { query ->
        if (query == null) return@mapLatest null
        query.album.also { album ->
            query.audios.forEach { audio ->
                album.link(audio)
                audio.link(album)
            }
        }
    }

    fun getAlbums(ids: List<String>) = getAlbumsWithAudios(ids)
        .mapLatest { list ->
            list?.map { query ->
                query.album.also { artist ->
                    query.audios.forEach { audio ->
                        artist.link(audio)
                        audio.link(artist)
                    }
                }
            }
        }

    @Transaction
    @Query("SELECT * FROM l_audio WHERE song_id IN (SELECT song_id FROM cross_ref_audio_x_album WHERE album_id = :albumId)")
    fun getAudiosByAlbum(albumId: String): Flow<List<LAudio>>
}

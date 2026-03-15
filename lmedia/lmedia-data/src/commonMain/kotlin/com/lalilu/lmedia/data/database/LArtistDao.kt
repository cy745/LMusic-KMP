package com.lalilu.lmedia.data.database

import androidx.room3.*
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.link
import com.lalilu.lmedia.entity.ref
import com.lalilu.lmedia.entity.relation.CrossRefLAudioXLArtist
import com.lalilu.lmedia.entity.relation.QueryLArtistWithLAudioList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface LArtistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: LArtist)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(item: List<LAudio>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelation(item: List<CrossRefLAudioXLArtist>)

    @Update
    suspend fun update(item: LArtist)

    @Delete
    suspend fun delete(item: LArtist)

    @Transaction
    suspend fun insertAll(artist: LArtist) {
        val audios = artist.ref<LAudio>()

        insert(artist)
        insertAll(audios)
        insertRelation(audios.map { CrossRefLAudioXLArtist(artist.id, it.id) })
    }

    @Transaction
    @Query("SELECT * FROM l_artist")
    fun getAllArtistWithAudios(): Flow<List<QueryLArtistWithLAudioList>>

    fun getAllArtist(): Flow<List<LArtist>> = getAllArtistWithAudios().map { list ->
        list.map {
            it.artist.also { artistEntry ->
                it.audios.forEach { song ->
                    artistEntry.link(song)
                    song.link(artistEntry)
                }
            }
        }
    }

    @Transaction
    @Query("SELECT * FROM l_artist WHERE artist_id = :artistId")
    fun getArtistWithAudios(artistId: String): Flow<QueryLArtistWithLAudioList>

    fun getArtist(artistId: String): Flow<LArtist> = getArtistWithAudios(artistId)
        .map {
            it.artist.also { artistEntry ->
                it.audios.forEach { song ->
                    artistEntry.link(song)
                    song.link(artistEntry)
                }
            }
        }
}
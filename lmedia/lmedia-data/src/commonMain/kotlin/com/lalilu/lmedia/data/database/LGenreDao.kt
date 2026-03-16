package com.lalilu.lmedia.data.database

import androidx.room3.*
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LGenre
import com.lalilu.lmedia.entity.link
import com.lalilu.lmedia.entity.relation.CrossRefLAudioXGenre
import com.lalilu.lmedia.entity.relation.QueryLGenreWithAudios
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

@OptIn(ExperimentalCoroutinesApi::class)
@Dao
interface LGenreDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(genre: LGenre)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(genres: List<LGenre>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelation(relations: List<CrossRefLAudioXGenre>)

    @Update
    suspend fun update(genre: LGenre)

    @Delete
    suspend fun delete(genre: LGenre)

    @Transaction
    @Query("SELECT * FROM l_genre")
    fun getAllGenreWithAudios(): Flow<List<QueryLGenreWithAudios>>

    fun getAllGenre(): Flow<List<LGenre>> = getAllGenreWithAudios().mapLatest { list ->
        list.map { query ->
            query.genre.also { genre ->
                query.audios.forEach { audio ->
                    genre.link(audio)
                    audio.link(genre)
                }
            }
        }
    }

    @Transaction
    @Query("SELECT * FROM l_genre WHERE genre_id = :id")
    fun getGenreWithAudios(id: String): Flow<QueryLGenreWithAudios>

    fun getGenre(id: String): Flow<LGenre?> = getGenreWithAudios(id).mapLatest { query ->
        query.genre.also { genre ->
            query.audios.forEach { audio ->
                genre.link(audio)
                audio.link(genre)
            }
        }
    }

    @Transaction
    @Query("SELECT * FROM l_audio WHERE song_id IN (SELECT song_id FROM cross_ref_audio_x_genre WHERE genre_id = :genreId)")
    fun getAudiosByGenre(genreId: String): Flow<List<LAudio>>
}

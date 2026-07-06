package com.lalilu.lmedia.data.database

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.lalilu.lmedia.data.entity.LAudioEntity
import com.lalilu.lmedia.data.entity.LGenreEntity
import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXGenre
import com.lalilu.lmedia.data.database.relation.QueryLGenreWithAudios
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

@OptIn(ExperimentalCoroutinesApi::class)
@Dao
interface LGenreDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(genre: LGenreEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(genres: List<LGenreEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelation(relations: List<CrossRefLAudioXGenre>)

    @Update
    suspend fun update(genre: LGenreEntity)

    @Delete
    suspend fun delete(genre: LGenreEntity)

    @Transaction
    @Query("SELECT * FROM l_genre")
    fun getAllGenreWithAudios(): Flow<List<QueryLGenreWithAudios>>

    fun getAllGenre(): Flow<List<LGenreEntity>> =
        getAllGenreWithAudios().mapLatest { list -> list.map { it.genre } }

    @Transaction
    @Query("SELECT * FROM l_genre WHERE genre_id = :id")
    fun getGenreWithAudios(id: String): Flow<QueryLGenreWithAudios?>

    fun getGenre(id: String): Flow<LGenreEntity?> =
        getGenreWithAudios(id).mapLatest { it?.genre }

    @Transaction
    @Query("SELECT * FROM l_audio WHERE song_id IN (SELECT song_id FROM cross_ref_audio_x_genre WHERE genre_id = :genreId)")
    fun getAudiosByGenre(genreId: String): Flow<List<LAudioEntity>>
}

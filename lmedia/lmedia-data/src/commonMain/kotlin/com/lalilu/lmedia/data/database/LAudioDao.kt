package com.lalilu.lmedia.data.database

import androidx.room3.*
import com.lalilu.lmedia.data.database.relation.QueryLAudioWithRelations
import com.lalilu.lmedia.data.entity.LAudioEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

@OptIn(ExperimentalCoroutinesApi::class)
@Dao
interface LAudioDao {
    @Insert
    suspend fun insert(audio: LAudioEntity)

    @Insert
    suspend fun insertAll(audios: List<LAudioEntity>)

    @Update
    suspend fun update(audio: LAudioEntity)

    @Delete
    suspend fun delete(audio: LAudioEntity)

    @Transaction
    @Query("SELECT * FROM l_audio")
    fun getAllAudioWithRelations(): Flow<List<QueryLAudioWithRelations>>

    fun getAllAudio(): Flow<List<LAudioEntity>> =
        getAllAudioWithRelations().mapLatest { list -> list.map { it.audio } }

    @Transaction
    @Query("SELECT * FROM l_audio WHERE song_id = :id")
    fun getAudioWithRelations(id: String): Flow<QueryLAudioWithRelations?>

    fun getAudio(id: String): Flow<LAudioEntity?> =
        getAudioWithRelations(id).mapLatest { it?.audio }

    @Transaction
    @Query("SELECT * FROM l_audio WHERE song_id IN (:ids)")
    fun getAudiosWithRelations(ids: List<String>): Flow<List<QueryLAudioWithRelations>>

    fun getAudios(ids: List<String>): Flow<List<LAudioEntity>> =
        getAudiosWithRelations(ids).mapLatest { list ->
            val map = list.associateBy { it.audio.id }
            ids.mapNotNull { map[it]?.audio }
        }

    @Transaction
    @Query("DELETE FROM l_audio WHERE available = false;")
    suspend fun clearUnavailableAudio()
}

package com.lalilu.lmedia.data.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.lalilu.lmedia.data.entity.LAudioEntity
import com.lalilu.lmedia.data.entity.LAlbumEntity
import com.lalilu.lmedia.data.entity.LArtistEntity
import com.lalilu.lmedia.data.entity.LGenreEntity
import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXAlbum
import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXGenre
import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXLArtist
import com.lalilu.lmedia.domain.source.Snapshot

@Dao
interface LMediaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudio(list: List<LAudioEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtist(list: List<LArtistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(list: List<LAlbumEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenre(list: List<LGenreEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtistRelation(list: List<CrossRefLAudioXLArtist>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbumRelation(list: List<CrossRefLAudioXAlbum>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenreRelation(list: List<CrossRefLAudioXGenre>)

    @Query("SELECT * FROM l_audio WHERE media_source_name = :source")
    suspend fun getAudioBySource(source: String): List<LAudioEntity>

    @Query("SELECT * FROM l_audio WHERE song_id IN (:ids)")
    suspend fun getAudioByIds(ids: List<String>): List<LAudioEntity>

    @Query("DELETE FROM cross_ref_audio_x_artist WHERE song_id IN (:songIds)")
    suspend fun deleteArtistRelationsBySongIds(songIds: List<String>)

    @Query("DELETE FROM cross_ref_audio_x_album WHERE song_id IN (:songIds)")
    suspend fun deleteAlbumRelationsBySongIds(songIds: List<String>)

    @Query("DELETE FROM cross_ref_audio_x_genre WHERE song_id IN (:songIds)")
    suspend fun deleteGenreRelationsBySongIds(songIds: List<String>)

    @Transaction
    suspend fun insert(snapshot: Snapshot, sourceName: String) {
        require(snapshot.audios.all { it.mediaSourceName == sourceName }) {
            "Snapshot contains audio owned by a different source: $sourceName"
        }

        val batch = MediaLibraryAssembler.assemble(snapshot.audios)
        val audioFromSource = getAudioBySource(sourceName)
        val audioMap = batch.audios.associateBy { it.id }

        if (audioMap.isNotEmpty()) {
            val conflicts = audioMap.keys
                .chunked(SQLITE_QUERY_CHUNK_SIZE)
                .flatMap { getAudioByIds(it) }
                .filter { it.mediaSourceName != sourceName }
            require(conflicts.isEmpty()) {
                "Audio id is already owned by another source: ${conflicts.joinToString { it.id }}"
            }
        }

        val audioToUpdate = audioFromSource
            .filter { audio -> audioMap[audio.id] == null }
            .map { it.copy(available = false) }

        insertAudio(batch.audios + audioToUpdate)
        insertArtist(batch.artists)
        insertAlbum(batch.albums)
        insertGenre(batch.genres)

        // 只替换本次仍然存在的歌曲关系；已标记不可用的旧歌曲保留原关系供用户识别。
        val incomingSongIds = batch.audios.map(LAudioEntity::id)
        incomingSongIds.chunked(SQLITE_QUERY_CHUNK_SIZE).forEach { songIds ->
            deleteArtistRelationsBySongIds(songIds)
            deleteAlbumRelationsBySongIds(songIds)
            deleteGenreRelationsBySongIds(songIds)
        }
        insertArtistRelation(batch.artistRelations)
        insertAlbumRelation(batch.albumRelations)
        insertGenreRelation(batch.genreRelations)
    }

    companion object {
        /** 为其他 DAO 参数留出空间，避免超过 SQLite 默认的变量上限。 */
        private const val SQLITE_QUERY_CHUNK_SIZE = 500
    }
}

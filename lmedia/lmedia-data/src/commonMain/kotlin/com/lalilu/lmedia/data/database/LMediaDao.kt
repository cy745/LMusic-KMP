package com.lalilu.lmedia.data.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.lalilu.lmedia.data.entity.LAlbumEntity
import com.lalilu.lmedia.data.entity.LArtistEntity
import com.lalilu.lmedia.data.entity.LAudioEntity
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

    @Transaction
    suspend fun insert(snapshot: Snapshot, sourceName: String) {
        val audioFromSource = getAudioBySource(sourceName)
        val audioMap = snapshot.audios.associateBy { it.id }

        val audioToUpdate = audioFromSource
            .filter { audio -> audioMap[audio.id] == null }
            .map { it.copy(available = false) }

        // Convert domain entities to Room entities
        val audioEntities = snapshot.audios.map { domain ->
            LAudioEntity(
                id = domain.id,
                title = domain.title,
                subtitle = domain.subtitle,
                mediaSourceName = domain.mediaSourceName,
                metadata = domain.metadata,
                extra = domain.extra,
                available = domain.available
            )
        }

        val artistEntities = snapshot.artists.map { domain ->
            LArtistEntity(
                id = domain.id,
                title = domain.title,
                subtitle = domain.subtitle,
                extra = domain.extra
            )
        }

        val albumEntities = snapshot.albums.map { domain ->
            LAlbumEntity(
                id = domain.id,
                title = domain.title,
                subtitle = domain.subtitle,
                extra = domain.extra
            )
        }

        val genreEntities = snapshot.genres.map { domain ->
            LGenreEntity(
                id = domain.id,
                title = domain.title,
                subtitle = domain.subtitle,
                extra = domain.extra
            )
        }

        // Insert all entities
        insertAudio(audioEntities + audioToUpdate)
        insertArtist(artistEntities)
        insertAlbum(albumEntities)
        insertGenre(genreEntities)

        // Build and insert relations from snapshot.relations map
        val artistRelations = snapshot.audios.flatMap { song ->
            val artistIds = snapshot.relations["com.lalilu.lmedia.domain.model.LArtist"]
                ?.get(song.id) ?: emptyList()
            artistIds.map { artistId ->
                CrossRefLAudioXLArtist(
                    songId = song.id,
                    artistId = artistId
                )
            }
        }

        val albumRelations = snapshot.audios.flatMap { song ->
            val albumIds = snapshot.relations["com.lalilu.lmedia.domain.model.LAlbum"]
                ?.get(song.id) ?: emptyList()
            albumIds.map { albumId ->
                CrossRefLAudioXAlbum(
                    songId = song.id,
                    albumId = albumId
                )
            }
        }

        val genreRelations = snapshot.audios.flatMap { song ->
            val genreIds = snapshot.relations["com.lalilu.lmedia.domain.model.LGenre"]
                ?.get(song.id) ?: emptyList()
            genreIds.map { genreId ->
                CrossRefLAudioXGenre(
                    songId = song.id,
                    genreId = genreId
                )
            }
        }

        insertArtistRelation(artistRelations)
        insertAlbumRelation(albumRelations)
        insertGenreRelation(genreRelations)
    }
}

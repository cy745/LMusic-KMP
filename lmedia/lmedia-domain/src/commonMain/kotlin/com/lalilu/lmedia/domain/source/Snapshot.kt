package com.lalilu.lmedia.domain.source

import com.lalilu.lmedia.domain.model.*
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Serializable
data class Snapshot(
    val audios: List<LAudio> = emptyList(),
    val albums: List<LAlbum> = emptyList(),
    val artists: List<LArtist> = emptyList(),
    val folders: List<LFolder> = emptyList(),
    val genres: List<LGenre> = emptyList(),
    val state: SnapshotState = SnapshotState.Idle,
    val relations: Map<String, Map<String, List<String>>> = emptyMap(),
    val updateTime: Long = Clock.System.now().toEpochMilliseconds()
) {
    companion object {
        val Idle = Snapshot(state = SnapshotState.Idle)
        val Loading = Snapshot(state = SnapshotState.Loading())
        val Empty = Snapshot(state = SnapshotState.Empty)
    }
}

/**
 * Builds a [Snapshot] from a list of [LAudio], deriving album/artist/genre entities
 * from each audio's [Metadata] fields.
 */
fun buildSnapshot(audios: List<LAudio>): Snapshot {
    val list = audios.distinctBy { it.idValue() }

    val albums = list
        .groupBy { song -> song.metadata.album }
        .map { (album, songs) ->
            val name = album.takeIf { !it.isNullOrBlank() } ?: "Unknown"
            LAlbum(
                id = "${LAlbum.ID_PREFIX}$name",
                title = name,
                subtitle = ""
            )
        }

    val artists = list
        .groupBy { song -> song.metadata.artist }
        .flatMap { (artist, songs) ->
            val nameStr = artist.takeIf { !it.isNullOrBlank() } ?: "Unknown"
            val names = nameStr.split('/', ';', '、', ',', '，')
                .distinctBy { it }

            names.map { name ->
                LArtist(
                    id = "${LArtist.ID_PREFIX}$name",
                    title = name,
                    subtitle = artist ?: ""
                )
            }
        }

    val genres = list
        .groupBy { song -> song.metadata.genre }
        .map { (genre, songs) ->
            val name = genre.takeIf { it.isNotBlank() } ?: "Unknown"
            LGenre(
                id = "${LGenre.ID_PREFIX}$name",
                title = name,
                subtitle = ""
            )
        }

    return Snapshot(
        audios = list,
        albums = albums,
        artists = artists,
        genres = genres,
        relations = buildRelations(audios = list),
        state = SnapshotState.Success
    )
}

/**
 * Builds a relation map from audio metadata.
 * The map structure: entityType → audioId → listOfRelatedEntityIds
 */
fun buildRelations(
    audios: List<LAudio>
): Map<String, Map<String, List<String>>> {
    val relations: MutableMap<String, MutableMap<String, MutableList<String>>> = mutableMapOf()

    relations.getOrPut("com.lalilu.lmedia.domain.model.LArtist") { mutableMapOf() }.apply {
        audios.forEach { audio ->
            val artistNames = (audio.metadata.artist ?: "Unknown")
                .split('/', ';', '、', ',', '，')
                .distinctBy { it }
            val ids = artistNames.map { "${LArtist.ID_PREFIX}$it" }
            this[audio.idValue()] = ids.toMutableList()
        }
    }

    relations.getOrPut("com.lalilu.lmedia.domain.model.LAlbum") { mutableMapOf() }.apply {
        audios.forEach { audio ->
            val albumName = audio.metadata.album ?: "Unknown"
            this[audio.idValue()] = mutableListOf("${LAlbum.ID_PREFIX}$albumName")
        }
    }

    relations.getOrPut("com.lalilu.lmedia.domain.model.LGenre") { mutableMapOf() }.apply {
        audios.forEach { audio ->
            val genreName = audio.metadata.genre ?: "Unknown"
            if (genreName.isNotBlank()) {
                this[audio.idValue()] = mutableListOf("${LGenre.ID_PREFIX}$genreName")
            }
        }
    }

    return relations
}

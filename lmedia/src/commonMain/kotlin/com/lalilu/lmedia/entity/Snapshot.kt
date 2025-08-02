package com.lalilu.lmedia.entity

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
    val updateTime: Long = Clock.System.now().toEpochMilliseconds()
) {
    companion object {
        val Empty = Snapshot()
    }
}

fun Array<Snapshot>.combineToOne(): Snapshot {
    return Snapshot(
        audios = map { it.audios }.flatten(),
        albums = map { it.albums }.flatten(),
        artists = map { it.artists }.flatten(),
        folders = map { it.folders }.flatten(),
        genres = map { it.genres }.flatten(),
        updateTime = maxOf { it.updateTime }
    )
}
package com.lalilu.lmedia.entity

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.serialization.Transient

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
    @Transient
    var isLoading = false
        internal set

    companion object {
        val Loading = Snapshot().also { it.isLoading = true }
        val Empty = Snapshot()
    }
}

@OptIn(ExperimentalTime::class)
fun Array<Snapshot>.combineToOne(): Snapshot {
    val isLoading = any { it.isLoading }
    var updateTime = maxOf { it.updateTime }

    // 如果加载成功，则更新时间
    if (!isLoading) {
        updateTime = Clock.System.now().toEpochMilliseconds()
    }

    return Snapshot(
        audios = map { it.audios }.flatten().distinctBy { it.id },
        albums = map { it.albums }.flatten().distinctBy { it.id },
        artists = map { it.artists }.flatten().distinctBy { it.id },
        folders = map { it.folders }.flatten().distinctBy { it.id },
        genres = map { it.genres }.flatten().distinctBy { it.id },
        updateTime = updateTime
    ).also { it.isLoading = isLoading }
}

fun List<LAudio>.buildSnapshot(): Snapshot {
    val list = this.distinctBy { it.id } // 去除重复的歌曲

    val albums = list
        .groupBy { song -> song.metadata.album }
        .map { (album, songs) ->
            LAlbum(
                id = album,
                title = album,
                subtitle = "",
                items = songs
            )
        }.link()

    val artists = list
        .groupBy { song -> song.metadata.artist }
        .map { (artist, songs) ->
            LArtist(
                id = artist,
                title = artist,
                subtitle = "",
                items = songs
            )
        }
        .separate()
        .merge()
        .link()

    val genres = list
        .groupBy { song -> song.metadata.genre }
        .map { (genre, songs) ->
            LGenre(
                id = genre,
                title = genre,
                subtitle = "",
                items = songs
            )
        }.link()

    return Snapshot(
        audios = list,
        albums = albums,
        artists = artists,
        genres = genres,
    )
}


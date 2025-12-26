package com.lalilu.lmedia.entity

import kotlinx.serialization.Serializable
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


@Serializable
@OptIn(ExperimentalTime::class)
sealed interface SnapshotState {
    @Serializable
    data object Idle : SnapshotState

    @Serializable
    data object Empty : SnapshotState

    @Serializable
    data object Success : SnapshotState

    @Serializable
    data class Loading(
        val message: String = "Loading...",
        val progress: Float = 0f
    ) : SnapshotState

    // 避免在序列化传输时使用
    data class LoadingDynamic(
        val message: () -> String = { "Loading..." },
        val progress: () -> Float = { 0f }
    ) : SnapshotState

    @Serializable
    data class Error(
        val message: String = "Error"
    ) : SnapshotState
}

inline fun <reified T : SnapshotState> T.priority(): Int = T::class.priority()
inline fun <reified T : SnapshotState> KClass<T>.priority(): Int {
    return when (T::class) {
        SnapshotState.Idle::class -> 0
        SnapshotState.Empty::class -> 1
        SnapshotState.Success::class -> 2
        SnapshotState.Loading::class -> 3
        SnapshotState.Error::class -> 4
        else -> -1
    }
}

@OptIn(ExperimentalTime::class)
@Serializable
data class Snapshot(
    val audios: List<LAudio> = emptyList(),
    val albums: List<LAlbum> = emptyList(),
    val artists: List<LArtist> = emptyList(),
    val folders: List<LFolder> = emptyList(),
    val genres: List<LGenre> = emptyList(),
    val state: SnapshotState = SnapshotState.Idle,
    val updateTime: Long = Clock.System.now().toEpochMilliseconds()
) {
    companion object {
        val Loading = Snapshot(state = SnapshotState.Loading())
        val Empty = Snapshot(state = SnapshotState.Empty)
    }
}

@OptIn(ExperimentalTime::class)
fun Array<Snapshot>.combineToOne(): Snapshot {
    return Snapshot(
        audios = map { it.audios }.flatten().distinctBy { it.id },
        albums = map { it.albums }.flatten().distinctBy { it.id },
        artists = map { it.artists }.flatten().distinctBy { it.id },
        folders = map { it.folders }.flatten().distinctBy { it.id },
        genres = map { it.genres }.flatten().distinctBy { it.id },
        state = maxBy { it.state.priority() }.state,  // 显示优先级最高的状态
        updateTime = maxOf { it.updateTime }
    )
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
        state = SnapshotState.Success
    )
}


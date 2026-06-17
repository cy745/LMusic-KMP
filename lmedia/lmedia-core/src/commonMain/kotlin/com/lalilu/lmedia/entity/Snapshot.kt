package com.lalilu.lmedia.entity

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    val relations: Map<String, Map<String, List<String>>> = emptyMap(),
    val updateTime: Long = Clock.System.now().toEpochMilliseconds()
) {
    companion object {
        val Idle = Snapshot(state = SnapshotState.Idle)
        val Loading = Snapshot(state = SnapshotState.Loading())
        val Empty = Snapshot(state = SnapshotState.Empty)
    }
}

fun StateFlow<Snapshot>.toComposeState(scope: CoroutineScope): State<SnapshotState> {
    return mutableStateOf(value.state).also { state ->
        onEach { runCatching { state.value = it.state } }
            .launchIn(scope)
    }
}

fun Snapshot.redirectToNewSource(sourceName: String) {
    // 重定向数据源至另外一个Source
    audios.forEach { audio -> audio.mediaSourceName = sourceName }
    albums.forEach { album -> album.ref<LAudio>().forEach { it.mediaSourceName = sourceName } }
    artists.forEach { artist -> artist.ref<LAudio>().forEach { it.mediaSourceName = sourceName } }
    folders.forEach { folder -> folder.ref<LAudio>().forEach { it.mediaSourceName = sourceName } }
    genres.forEach { genre -> genre.ref<LAudio>().forEach { it.mediaSourceName = sourceName } }
}

@Deprecated("不再直接在Flow上处理数据合并")
@OptIn(ExperimentalTime::class)
fun Array<Snapshot>.combineToOne(): Snapshot {
    return Snapshot(
        audios = map { it.audios }.flatten().distinctBy { it.idValue() },
        albums = map { it.albums }.flatten().distinctBy { it.idValue() },
        artists = map { it.artists }.flatten().distinctBy { it.idValue() },
        folders = map { it.folders }.flatten().distinctBy { it.idValue() },
        genres = map { it.genres }.flatten().distinctBy { it.idValue() },
        state = maxBy { it.state.priority() }.state,  // 显示优先级最高的状态
        updateTime = maxOf { it.updateTime }
    )
}

fun List<LAudio>.buildSnapshot(): Snapshot {
    val list = this.distinctBy { it.idValue() } // 去除重复的歌曲

    val albums = list
        .groupBy { song -> song.metadata.album }
        .map { (album, songs) ->
            val name = album.takeIf { !it.isNullOrBlank() } ?: "Unknown"
            LAlbum(
                id = "${LAlbum.ID_PREFIX}$name",
                title = name,
                subtitle = ""
            ).also { albumEntity ->
                songs.forEach { song ->
                    song.link(albumEntity)
                    albumEntity.link(song)
                }
            }
        }

    val artists = list
        .groupBy { song -> song.metadata.artist }
        .map { (artist, songs) ->
            val nameStr = artist.takeIf { !it.isNullOrBlank() } ?: "Unknown"
            val names = nameStr.split('/', ';', '、', ',', '，')
                .distinctBy { it }

            names.map { name ->
                LArtist(
                    id = "${LArtist.ID_PREFIX}$name",
                    title = name,
                    subtitle = artist ?: ""
                ).also { artistEntity ->
                    songs.forEach { song ->
                        song.link(artistEntity)
                        artistEntity.link(song)
                    }
                }
            }
        }.flatten()

    val genres = list
        .groupBy { song -> song.metadata.genre }
        .map { (genre, songs) ->
            val name = genre.takeIf { it.isNotBlank() } ?: "Unknown"
            LGenre(
                id = "${LGenre.ID_PREFIX}$name",
                title = name,
                subtitle = ""
            ).also { genreEntity ->
                songs.forEach { song ->
                    song.link(genreEntity)
                    genreEntity.link(song)
                }
            }
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

fun buildRelations(
    audios: List<LAudio>
): Map<String, Map<String, List<String>>> {
    val relations: MutableMap<String, MutableMap<String, MutableList<String>>> = mutableMapOf()

    relations.getOrPut(LArtist::class.qualifiedName!!) { mutableMapOf() }.apply {
        audios.forEach { audio ->
            val artists = audio.ref<LArtist>()
            val list = getOrPut(audio.idValue()) { mutableListOf() }
            list.addAll(artists.map { it.idValue() })
        }
    }

    relations.getOrPut(LAlbum::class.qualifiedName!!) { mutableMapOf() }.apply {
        audios.forEach { audio ->
            val albums = audio.ref<LAlbum>()
            val list = getOrPut(audio.idValue()) { mutableListOf() }
            list.addAll(albums.map { it.idValue() })
        }
    }

    return relations
}

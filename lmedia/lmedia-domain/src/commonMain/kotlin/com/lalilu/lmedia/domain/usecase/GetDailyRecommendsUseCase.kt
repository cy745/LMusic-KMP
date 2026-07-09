package com.lalilu.lmedia.domain.usecase

import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AlbumRepository
import com.lalilu.lmedia.domain.repository.ArtistRepository
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.repository.DailyRecommendsStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * A recommendation item that wraps a concrete domain model.
 */
sealed class RecommendItem {
    abstract val id: String
    abstract val title: String
    abstract val subtitle: String

    data class Audio(val audio: LAudio) : RecommendItem() {
        override val id get() = audio.id
        override val title get() = audio.title
        override val subtitle get() = audio.subtitle
    }

    data class Album(val album: LAlbum) : RecommendItem() {
        override val id get() = album.id
        override val title get() = album.title
        override val subtitle get() = album.subtitle
    }

    data class Artist(val artist: LArtist) : RecommendItem() {
        override val id get() = artist.id
        override val title get() = artist.title
        override val subtitle get() = artist.subtitle
    }
}

/**
 * Manages daily recommended items (audios, albums, artists).
 * Extracted from HomeScreenModel.
 */
@org.koin.core.annotation.Single
class GetDailyRecommendsUseCase(
    private val audioRepository: AudioRepository,
    private val albumRepository: AlbumRepository,
    private val artistRepository: ArtistRepository,
    private val storage: DailyRecommendsStorage
) {
    /**
     * Flow of current daily recommend items, mapped from stored keys.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun get(): Flow<List<RecommendItem>> {
        return storage.flow().flatMapLatest { keys ->
            combine(
                audioRepository.getAudios().map { audios ->
                    audios.filter { it.id in keys }.map { RecommendItem.Audio(it) }
                },
                albumRepository.getAlbums().map { albums ->
                    albums.filter { it.id in keys }.map { RecommendItem.Album(it) }
                },
                artistRepository.getArtists().map { artists ->
                    artists.filter { it.id in keys }.map { RecommendItem.Artist(it) }
                }
            ) { audioItems, albumItems, artistItems ->
                mutableListOf<RecommendItem>().apply {
                    addAll(audioItems)
                    addAll(albumItems)
                    addAll(artistItems)
                }.distinctBy { it.id }
            }
        }
    }

    /**
     * Whether the daily recommend list needs refreshing (empty or all items unavailable).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun needsRefresh(): Flow<Boolean> {
        return storage.flow()
            .combine(audioRepository.getAudios()) { keys, audios ->
                (keys.isEmpty() || getCurrentItems(keys).isEmpty()) && audios.isNotEmpty()
            }
            .distinctUntilChanged()
    }

    /**
     * Generate and persist a fresh set of daily recommendations.
     */
    suspend fun refresh() {
        val audios = audioRepository.getAudios().firstOrNull() ?: return
        val artists = artistRepository.getArtists().firstOrNull() ?: emptyList()
        val albums = albumRepository.getAlbums().firstOrNull() ?: emptyList()

        val buildItems = buildList {
            addAll(audios.shuffled().take(10).map { it.id })
            addAll(albums.shuffled().take(2).map { it.id })
            addAll(artists.shuffled().take(2).map { it.id })
        }.shuffled()

        storage.set(buildItems)
    }

    private suspend fun getCurrentItems(keys: List<String>): List<RecommendItem> {
        val audios = audioRepository.getAudios().firstOrNull() ?: emptyList()
        val albums = albumRepository.getAlbums().firstOrNull() ?: emptyList()
        val artists = artistRepository.getArtists().firstOrNull() ?: emptyList()

        return buildList {
            addAll(audios.filter { it.id in keys }.map { RecommendItem.Audio(it) })
            addAll(albums.filter { it.id in keys }.map { RecommendItem.Album(it) })
            addAll(artists.filter { it.id in keys }.map { RecommendItem.Artist(it) })
        }.distinctBy { it.id }
    }
}

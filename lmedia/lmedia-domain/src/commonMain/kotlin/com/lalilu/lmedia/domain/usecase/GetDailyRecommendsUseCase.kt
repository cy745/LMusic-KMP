package com.lalilu.lmedia.domain.usecase

import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LItem
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
 * Manages daily recommended items (audios, albums, artists).
 * Extracted from HomeScreenModel.
 */
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
    fun get(): Flow<List<LItem>> {
        return storage.flow().flatMapLatest { keys ->
            combine(
                audioRepository.getAudios().map { audios ->
                    audios.filter { it.idValue() in keys }.map<LAudio, LItem> { it }
                },
                albumRepository.getAlbums().map { albums ->
                    albums.filter { it.idValue() in keys }.map<LAlbum, LItem> { it }
                },
                artistRepository.getArtists().map { artists ->
                    artists.filter { it.idValue() in keys }.map<LArtist, LItem> { it }
                }
            ) { audioItems, albumItems, artistItems ->
                mutableListOf<LItem>().apply {
                    addAll(audioItems)
                    addAll(albumItems)
                    addAll(artistItems)
                }.distinctBy { it.idValue() }
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
            addAll(audios.shuffled().take(10).map { it.idValue() })
            addAll(albums.shuffled().take(2).map { it.idValue() })
            addAll(artists.shuffled().take(2).map { it.idValue() })
        }.shuffled()

        storage.set(buildItems)
    }

    private suspend fun getCurrentItems(keys: List<String>): List<LItem> {
        val audios = audioRepository.getAudios().firstOrNull() ?: emptyList()
        val albums = albumRepository.getAlbums().firstOrNull() ?: emptyList()
        val artists = artistRepository.getArtists().firstOrNull() ?: emptyList()

        return buildList {
            addAll(audios.filter { it.idValue() in keys })
            addAll(albums.filter { it.idValue() in keys })
            addAll(artists.filter { it.idValue() in keys })
        }.distinctBy { it.idValue() }
    }
}

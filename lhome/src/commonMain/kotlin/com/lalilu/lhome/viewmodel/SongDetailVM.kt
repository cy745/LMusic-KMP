package com.lalilu.lhome.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lalilu.extensions.toState
import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.model.libraryAlbumId
import com.lalilu.lmedia.domain.model.artistNames
import com.lalilu.lmedia.domain.model.libraryArtistIds
import com.lalilu.lmedia.domain.model.LAudio as DomainAudio
import com.lalilu.lmedia.domain.repository.AlbumRepository
import com.lalilu.lmedia.domain.repository.ArtistRepository
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.repository.getAudioByPlaybackId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.Factory


@Factory
class SongDetailVM(
    val mediaId: String,
    private val audioRepository: AudioRepository,
    private val albumRepository: AlbumRepository,
    private val artistRepository: ArtistRepository
) : ViewModel() {

    val flow = audioRepository.getAudioByPlaybackId(mediaId)
        .mapLatest { it }
        .stateIn(viewModelScope, started = SharingStarted.Lazily, null)

    val songState = flow.toState(viewModelScope)

    val albums = flow.flatMapLatest { audio ->
        if (audio == null) flowOf(emptyList())
        else albumRepository.getAlbum(audio.libraryAlbumId()).map { listOfNotNull(it) }
    }.stateIn(viewModelScope, started = SharingStarted.Lazily, emptyList<LAlbum>())

    val artists = flow.flatMapLatest { audio ->
        if (audio == null) flowOf(emptyList())
        else artistRepository.getArtists(audio.libraryArtistIds()).map { orderSongArtists(audio, it) }
    }.stateIn(viewModelScope, started = SharingStarted.Lazily, emptyList<LArtist>())
}

/** Repository query order is not the order of credits in the song metadata. */
internal fun orderSongArtists(audio: DomainAudio, artists: List<LArtist>): List<LArtist> {
    val byId = artists.associateBy { it.id }
    return audio.libraryArtistIds().zip(audio.artistNames()).mapNotNull { (id, name) ->
        byId[id]?.copy(title = name)
    }
}

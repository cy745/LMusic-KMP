package com.lalilu.lhome.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lalilu.extensions.toState
import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.model.LAudio as DomainAudio
import com.lalilu.lmedia.domain.repository.AlbumRepository
import com.lalilu.lmedia.domain.repository.ArtistRepository
import com.lalilu.lmedia.domain.repository.AudioRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.firstOrNull
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

    val flow = audioRepository.getAudio(mediaId)
        .mapLatest { it }
        .stateIn(viewModelScope, started = SharingStarted.Lazily, null)

    val songState = flow.toState(viewModelScope)

    val albums = flow.mapLatest {
        it ?: return@mapLatest emptyList<LAlbum>()
        val albumName = it.metadata.album ?: return@mapLatest emptyList<LAlbum>()
        albumRepository.getAlbum("${com.lalilu.lmedia.domain.model.LAlbum.ID_PREFIX}$albumName").firstOrNull()
            ?.let { listOf(it) } ?: emptyList()
    }.stateIn(viewModelScope, started = SharingStarted.Lazily, null)

    val artists = flow.mapLatest {
        it ?: return@mapLatest emptyList<LArtist>()
        val artistName = it.metadata.artist ?: return@mapLatest emptyList<LArtist>()
        val names = artistName.split('/', ';', '、', ',', '，').distinctBy { it }
        val artistIds = names.map { "${com.lalilu.lmedia.domain.model.LArtist.ID_PREFIX}$it" }
        artistRepository.getArtists(artistIds).firstOrNull()
            ?.map { it }
            ?: emptyList()
    }.stateIn(viewModelScope, started = SharingStarted.Lazily, null)
}

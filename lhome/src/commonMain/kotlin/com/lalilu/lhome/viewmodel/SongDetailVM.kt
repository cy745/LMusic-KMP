package com.lalilu.lhome.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lalilu.lmedia.data.LMedia
import com.lalilu.lmedia.entity.LAlbum
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.ref
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.Factory


@Factory
class SongDetailVM(val mediaId: String) : ViewModel() {

    val flow = LMedia.instance.flow<LAudio>(mediaId)
        .stateIn(viewModelScope, started = SharingStarted.Lazily, null)

    val albums = flow.mapLatest {
        val list = it?.ref<LAlbum>() ?: emptyList()
        if (list.isEmpty()) return@mapLatest emptyList()
        LMedia.instance.mapByByPrefix(list.map { it.idValue() })
            .filterIsInstance<LAlbum>()
    }.stateIn(viewModelScope, started = SharingStarted.Lazily, null)

    val artists = flow.mapLatest {
        val list = it?.ref<LAudio>() ?: emptyList()
        if (list.isEmpty()) return@mapLatest emptyList()
        LMedia.instance.mapByByPrefix(list.map { it.idValue() })
            .filterIsInstance<LArtist>()
    }.stateIn(viewModelScope, started = SharingStarted.Lazily, null)


}
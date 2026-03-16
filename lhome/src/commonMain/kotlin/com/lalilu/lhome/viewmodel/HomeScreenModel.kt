package com.lalilu.lhome.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lalilu.extensions.toState
import com.lalilu.lhome.LHomeKV
import com.lalilu.lmedia.data.LMedia
import com.lalilu.lmedia.data.Library
import com.lalilu.lmedia.entity.LAlbum
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import com.russhwolf.settings.ExperimentalSettingsApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import org.koin.core.annotation.Factory

@Factory
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSettingsApi::class)
class HomeScreenModel(
    private val library: Library,
    private val lHomeKV: LHomeKV
) : ViewModel() {
    val recentlyAdded = LMedia.instance.flow<LAudio>()
        .mapLatest { it.take(15) }
        .toState(emptyList(), viewModelScope)

    val histories = LMedia.instance.flow<LAudio>()
        .mapLatest { it.shuffled().take(6) }
        .toState(emptyList(), viewModelScope)

    val dailyRecommends = lHomeKV.dailyRecommends.flow()
        .flatMapLatest { list ->
            library.flow<LAudio>().map { audios ->
                mutableListOf<LItem>().apply {
                    val artists = library.get<LArtist>()
                    val albums = library.get<LAlbum>()

                    addAll(audios.filter { it.idValue() in list })
                    addAll(albums.filter { it.idValue() in list })
                    addAll(artists.filter { it.idValue() in list })
//                    addAll(snapshot.genres.filter { it.idValue() in list })
//                    addAll(snapshot.folders.filter { it.idValue() in list })
                }.distinctBy { it.idValue() }
            }
        }
        .toState(emptyList(), viewModelScope)

    fun requireUpdateDailyRecommends() = viewModelScope.launch {
        val buildItems = buildList {
            val audios = library.get<LAudio>()
            val artists = library.get<LArtist>()
            val albums = library.get<LAlbum>()

            addAll(audios.shuffled().take(10).map { it.idValue() })
            addAll(albums.shuffled().take(2).map { it.idValue() })
            addAll(artists.shuffled().take(2).map { it.idValue() })
//                    addAll(snapshot.genres.filter { it.idValue() in list })
//                    addAll(snapshot.folders.filter { it.idValue() in list })
        }.shuffled()

        lHomeKV.dailyRecommends.setData(buildItems)
    }
}
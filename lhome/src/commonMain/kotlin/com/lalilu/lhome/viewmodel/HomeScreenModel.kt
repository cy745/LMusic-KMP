package com.lalilu.lhome.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lalilu.extensions.toState
import com.lalilu.lhome.LHomeKV
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import com.lalilu.lmedia.source.Library
import com.russhwolf.settings.ExperimentalSettingsApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import org.koin.core.annotation.Factory

@Factory
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSettingsApi::class)
class HomeScreenModel(
    private val library: Library,
    private val lHomeKV: LHomeKV
) : ViewModel() {
    val recentlyAdded = library.getFlow<LAudio>()
        .mapLatest { it.take(15) }
        .toState(emptyList(), viewModelScope)

    val histories = library.getFlow<LAudio>()
        .mapLatest { it.shuffled().take(6) }
        .toState(emptyList(), viewModelScope)

    val dailyRecommends = lHomeKV.dailyRecommends.flow()
        .flatMapLatest { list ->
            library.snapshotStateFlow.mapLatest { snapshot ->
                mutableListOf<LItem>().apply {
                    addAll(snapshot.audios.filter { it.id in list })
                    addAll(snapshot.albums.filter { it.id in list })
                    addAll(snapshot.artists.filter { it.id in list })
                    addAll(snapshot.genres.filter { it.id in list })
                    addAll(snapshot.folders.filter { it.id in list })
                }
            }
        }
        .toState(emptyList(), viewModelScope)

    fun requireUpdateDailyRecommends() = viewModelScope.launch {
        val buildItems = buildList {
            library.snapshotStateFlow.value.apply {
                addAll(audios.shuffled().take(10).map { it.id })
                addAll(albums.shuffled().take(2).map { it.id })
                addAll(artists.shuffled().take(2).map { it.id })
                addAll(genres.shuffled().take(1).map { it.id })
                addAll(folders.shuffled().take(1).map { it.id })
            }
        }.shuffled()

        lHomeKV.dailyRecommends.setData(buildItems)
    }
}
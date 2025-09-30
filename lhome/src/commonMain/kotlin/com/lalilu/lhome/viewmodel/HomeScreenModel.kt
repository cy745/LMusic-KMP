package com.lalilu.lhome.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lalilu.extensions.toState
import com.lalilu.lhome.LHomeKV
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.source.Library
import com.russhwolf.settings.ExperimentalSettingsApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
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

    val dailyRecommends = lHomeKV.dailyRecommends.flow()
        .flatMapLatest { library.flowMapBy<LAudio>(it) }
        .toState(emptyList(), viewModelScope)
}
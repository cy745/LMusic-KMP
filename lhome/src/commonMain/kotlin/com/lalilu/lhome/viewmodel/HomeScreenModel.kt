package com.lalilu.lhome.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lalilu.lhome.LHomeKV
import com.lalilu.lmedia.data.LMedia
import com.lalilu.lmedia.data.Library
import com.lalilu.lmedia.entity.LAlbum
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import com.russhwolf.settings.ExperimentalSettingsApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

@Single
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSettingsApi::class)
class HomeScreenModel(
    private val library: Library,
    private val lHomeKV: LHomeKV
) : ViewModel() {
    /**
     * 使用 stateIn(SharingStarted.Lazily) 而非 toState() 延迟订阅：
     *  - toState() 内部 .onEach{}.launchIn(viewModelScope) 启动协程后立即 collect
     *  - stateIn(Lazily) 仅在第一次有订阅者时才启动 collect
     * 在冷启动场景下，HomeScreenModel 在 HomeScreenContent 第一次 measure 时构造，
     * Lazily 让首帧先绘制空 UI，再异步加载数据；订阅触发后 3 个 Room SQL query
     * (Audio/Artist/Album) 串行执行约 200ms。
     */
    val recentlyAdded: StateFlow<List<LAudio>> = LMedia.instance.flow<LAudio>()
        .mapLatest { it.take(15) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    val dailyRecommends: StateFlow<List<LItem>> = lHomeKV.dailyRecommends.flow()
        .flatMapLatest { list ->
            library.flow<LAudio>().map { audios ->
                mutableListOf<LItem>().apply {
                    val artists = library.get<LArtist>()
                    val albums = library.get<LAlbum>()

                    addAll(audios.filter { it.idValue() in list })
                    addAll(albums.filter { it.idValue() in list })
                    addAll(artists.filter { it.idValue() in list })
                }.distinctBy { it.idValue() }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    init {
        // 确保每日推荐列表不为空
        lHomeKV.dailyRecommends.flow()
            .combine(library.flow<LAudio>()) { keys, audios ->
                val recommends = library.mapByByPrefix(keys)
                (keys.isEmpty() || recommends.isEmpty()) && audios.isNotEmpty()
            }
            .distinctUntilChanged()
            .onEach { needRefresh -> if (needRefresh) requireUpdateDailyRecommends() }
            .launchIn(viewModelScope)
    }

    fun requireUpdateDailyRecommends() = viewModelScope.launch {
        val buildItems = buildList {
            val audios = library.get<LAudio>()
            val artists = library.get<LArtist>()
            val albums = library.get<LAlbum>()

            addAll(audios.shuffled().take(10).map { it.idValue() })
            addAll(albums.shuffled().take(2).map { it.idValue() })
            addAll(artists.shuffled().take(2).map { it.idValue() })
        }.shuffled()

        lHomeKV.dailyRecommends.setData(buildItems)
    }
}

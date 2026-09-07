package com.lalilu.lplayer.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.*
import com.lalilu.common.ext.io
import com.lalilu.llyric.LyricItem
import com.lalilu.llyric.LyricUtils
import com.lalilu.llyricview.LyricContent
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.source.MediaContentAvailability
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.resolveLyricData
import com.lalilu.lplayer.LPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@OptIn(ExperimentalCoroutinesApi::class)
@Single(binds = [ViewModel::class])
class PlayerViewModel(
    private val platformSource: PlatformMediaSource
) : ViewModel(), LifecycleEventObserver {
    val isPlaying = LPlayer.instance.isPlaying
    val currentItem = LPlayer.instance.queue.currentItemFlow()
    val lyricContent = mutableStateOf<LyricContent>(LyricContent.Loading(null))

    val currentQueue = LPlayer.instance.queue.expandedItems
        .mapLatest { it.rearrange() }

    init {
        currentItem
            .flatMapLatest { audio ->
                val source = audio?.let { song ->
                    platformSource.findSource(song.mediaSourceName)
                }
                if (audio == null || source == null) {
                    flowOf(
                        LyricContent.Ready(key = audio?.id, items = emptyList()),
                    )
                } else {
                    observeLyricContent(audio, source) { retrieveLyric(audio) }
                }
            }
            .onEach { lyricContent.value = it }
            .launchIn(viewModelScope)
    }

    suspend fun retrieveLyric(audio: LAudio?): List<LyricItem> = withContext(Dispatchers.io) {
        val song = audio ?: return@withContext emptyList()
        val lyric = try {
            platformSource.resolveLyricData(song)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }

        return@withContext LyricUtils.parseLrc(lyric)
            ?: emptyList()
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_PAUSE -> {}
            Lifecycle.Event.ON_START -> {}
            else -> {}
        }
    }
}

/**
 * 新歌词准备期间不发射，以便保留旧文档；明确不可用时则发布当前歌曲的空文档作为终止状态，
 * 让歌词页完成媒体身份切换并恢复自身的滑动手势状态。
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun observeLyricContent(
    audio: LAudio,
    source: MediaSource,
    retrieve: suspend () -> List<LyricItem>,
): Flow<LyricContent> = source.contentState.transformLatest { contentState ->
    when (val availability = contentState.availability) {
        MediaContentAvailability.Ready -> emit(
            LyricContent.Ready(
                key = audio.id,
                generation = contentState.generation,
                items = retrieve(),
            )
        )

        is MediaContentAvailability.Unavailable -> emit(
            LyricContent.Ready(
                key = audio.id,
                generation = contentState.generation,
                items = emptyList(),
                emptyMessage = "数据源不可用，无法加载歌词",
            )
        )

        MediaContentAvailability.Preparing,
        MediaContentAvailability.Uninitialized -> Unit
    }
}

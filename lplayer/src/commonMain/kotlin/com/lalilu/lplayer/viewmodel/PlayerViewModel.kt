package com.lalilu.lplayer.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.*
import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.llyric.LyricItem
import com.lalilu.llyric.LyricUtils
import com.lalilu.llyricview.LyricContent
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaContentAvailability
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.source.resolveLyricData
import com.lalilu.lplayer.LPlayer
import com.lalilu.lplayer.components.PlaylistItems
import com.lalilu.lplayer.playback.QueueState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.time.TimeSource

@OptIn(ExperimentalCoroutinesApi::class)
@Single(binds = [ViewModel::class])
class PlayerViewModel(
    private val platformSource: PlatformMediaSource
) : ViewModel(), LifecycleEventObserver {
    private val logger = Logger.withTag("PlayerLyrics")
    val isPlaying = LPlayer.instance.isPlaying
    val currentItem = LPlayer.instance.queue.currentItemFlow().distinctUntilChanged()
    val lyricContent = mutableStateOf<LyricContent>(LyricContent.Loading(null))

    /** 播放列表数据 + 这次变化的成因（成因由队列命令边界给出，见 [QueueState.currentPickedByUser]） */
    val currentQueue = LPlayer.instance.queue.expandedItems
        .mapLatest { PlaylistItems(items = it.rearrange(), pickedByUser = it.currentPickedByUser) }
        .distinctUntilChanged()

    init {
        currentItem
            .flatMapLatest { audio ->
                logger.i { "当前曲目变化：${audio?.id}（${audio?.title}）来源=${audio?.mediaSourceName}" }
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
            .onEach {
                lyricContent.value = it
                logger.i {
                    "歌词内容更新：key=${it.key} gen=${it.generation} " +
                        "items=${(it as? LyricContent.Ready)?.items?.size ?: -1}"
                }
            }
            .launchIn(viewModelScope)
    }

    suspend fun retrieveLyric(audio: LAudio?): List<LyricItem> = withContext(Dispatchers.io) {
        val song = audio ?: return@withContext emptyList()
        logger.i { "开始取歌词：${song.title}" }
        val startedAt = TimeSource.Monotonic.markNow()
        val lyric = try {
            platformSource.resolveLyricData(song)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }

        val items = LyricUtils.parseLrc(lyric) ?: emptyList()
        logger.i {
            "歌词解析：${song.title} 原始=${lyric?.length ?: 0} 字符 → ${items.size} 行 " +
                "用时 ${startedAt.elapsedNow().inWholeMilliseconds}ms"
        }
        return@withContext items
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
): Flow<LyricContent> = source.contentState
    .map { it.forItem(audio.id) }
    .distinctUntilChanged()
    .transformLatest { contentState ->
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

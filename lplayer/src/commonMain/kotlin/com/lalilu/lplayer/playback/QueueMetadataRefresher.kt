package com.lalilu.lplayer.playback

import co.touchlab.kermit.Logger
import com.lalilu.lmedia.domain.repository.AudioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * 持续用数据库中的最新歌曲实体刷新播放队列，但不改变队列顺序、当前位置和播放器引擎状态。
 *
 * 播放历史可以先从数据库立即恢复；之后任一数据源完成写入时，Room Flow 会推送包含新封面定位、
 * 标题等信息的 LAudio，本刷新器只替换队列中的对象。不存在于数据库的项暂时保留，避免某个慢数据源
 * 尚未完成时，其他数据源的提交导致队列被提前截断。
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun CoroutineScope.startQueueMetadataRefresh(
    queue: PlayableQueue,
    audioRepository: AudioRepository,
) = queue.expandedItems
    .map { state -> state.list.map { it.id } }
    .distinctUntilChanged()
    .filter(List<String>::isNotEmpty)
    .flatMapLatest { ids ->
        audioRepository.getAudios(ids).map { audios -> ids to audios }
    }
    .onEach { (observedIds, refreshed) ->
        val current = queue.stateSnapshot()
        if (current.list.map { it.id } != observedIds) return@onEach

        val refreshedById = refreshed.associateBy { it.id }
        val updated = current.list.map { old -> refreshedById[old.id] ?: old }
        if (updated == current.list) return@onEach

        queue.update(
            updateReason = QueueUpdateReason.Sync,
            predicate = { state -> state.list.map { it.id } == observedIds },
        ) {
            // index = -1 会根据原子区间内的当前歌曲重新定位，避免覆盖同时发生的切歌。
            replaceAll(items = updated, index = -1)
        }
    }
    .catch { throwable ->
        Logger.e(
            tag = "QueueMetadataRefresher",
            messageString = "Failed to refresh queue metadata",
            throwable = throwable,
        )
    }
    .launchIn(this)

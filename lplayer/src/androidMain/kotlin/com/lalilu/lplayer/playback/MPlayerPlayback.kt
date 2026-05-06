package com.lalilu.lplayer.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.lalilu.lmedia.data.Library
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import com.lalilu.lplayer.LPlayerKV
import com.lalilu.lplayer.extensions.PlayMode
import com.lalilu.lplayer.extensions.playMode
import com.lalilu.lplayer.extensions.toMediaItem
import com.lalilu.lplayer.service.CustomCommand
import com.lalilu.lplayer.service.MService
import io.github.petertrr.diffutils.algorithm.myers.MyersDiff
import io.github.petertrr.diffutils.patch.DeltaType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.guava.await
import kotlin.collections.map
import kotlin.coroutines.CoroutineContext

@OptIn(UnstableApi::class, ExperimentalCoroutinesApi::class)
class MPlayerPlayback(
    private val context: Context,
    private val library: Library
) : CoroutineScope, Player.Listener, Playback, Runnable {
    override val coroutineContext: CoroutineContext = Dispatchers.IO
    private val sessionToken by lazy {
        SessionToken(context, ComponentName(context, MService::class.java))
    }

    private var loopJob: Job? = null
    private var browserInstance: MediaBrowser? = null
    private val browserFuture by lazy {
        MediaBrowser
            .Builder(context, sessionToken)
            .buildAsync()
    }

    override val queue: PlayableQueue = PlayableQueueImpl()

    var pauseWhenCompletion: Boolean by mutableStateOf(false)
        private set

    // Protected mutable state flows
    private val _isPlaying = MutableStateFlow(false)
    private val _errors = MutableSharedFlow<Throwable>()
    private val _currentDuration = MutableStateFlow(0L)
    private val _currentBufferedPosition = MutableStateFlow(0L)
    private val _playbackMode = MutableStateFlow(PlaybackMode.SEQUENTIAL)

    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    override val errors: SharedFlow<Throwable> = _errors.asSharedFlow()
    override val currentDuration: StateFlow<Long> = _currentDuration.asStateFlow()
    override val currentBufferedPosition: StateFlow<Long> = _currentBufferedPosition.asStateFlow()
    override val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()

    init {
        browserFuture.addListener(this@MPlayerPlayback, Dispatchers.Main.asExecutor())
    }

    /**
     * browser 连接成功回调
     */
    override fun run() {
        val browser = browserFuture.get() ?: return
        browserInstance = browser
        browser.addListener(this@MPlayerPlayback)

        launch {
            val lastPosition = LPlayerKV.historyPlayPosition.value
            val lastMediaIds = LPlayerKV.historyPlaylistIds.value
            val lastParentIds = LPlayerKV.historyPlaylistParentIds.value
            val mediaIdsParentIdsMap = run {
                if (lastMediaIds.size != lastParentIds.size) return@run emptyList()
                lastMediaIds.mapIndexed { index, string -> string to lastParentIds[index] }
            }

            val lastPlayId = LPlayerKV.historyPlayId.value
            val lastPlayIndex = lastMediaIds.indexOf(lastPlayId)
            val items = library.mapBy<LAudio>(lastMediaIds)

            val playableList = with(queue) {
                items.mapIndexed { index, audio ->
                    val pair = mediaIdsParentIdsMap.getOrNull(index)
                    if (pair?.first == audio.idValue() && pair.second.isNotBlank()) {
                        val parent = library.getByPrefix(pair.second)
                        val sourceItem = parent?.toPlayable() as Playable.Items<LAudio, *>
                        return@mapIndexed Playable.Item(audio, sourceItem)
                    }
                    audio.toPlayable()
                }
            }

            queue.replaceAll(playableList, lastPlayIndex)
            diffUpdateMediaItems(items)

            withContext(Dispatchers.Main) {
                browser.playWhenReady = LPlayerKV.autoPlayWhenRestart.value
                browser.seekTo(lastPlayIndex, lastPosition)
                browser.prepare()
            }

            // 监听播放列表更新，并刷新播放列表
            queue.expandedItems
                .onEach { (list, index) ->
                    val ids = list.map { it.item.idValue() }
                    LPlayerKV.historyPlaylistIds.value = ids
                    LPlayerKV.historyPlaylistParentIds.value = list.map { it.source?.source?.idValue() ?: "" }
                    LPlayerKV.historyPlayId.value = ids.getOrNull(index) ?: ""
                    diffUpdateMediaItems(list.map { it.item })
                }
                .launchIn(this@MPlayerPlayback)
        }
    }

    override fun currentPosition(): Long {
        return runCatching { if (browserFuture.isDone) browserFuture.get()?.currentPosition else null }
            .getOrNull() ?: 0L
    }

    override suspend fun play() = runWithBrowser {
        play()
    }

    override suspend fun pause() = runWithBrowser {
        pause()
    }

    override suspend fun togglePlayPause() = runWithBrowser {
        if (isPlaying) pause() else play()
    }

    override suspend fun stop() = runWithBrowser {
        stop()
    }

    override suspend fun skipTo(index: Int, start: Boolean) = runWithBrowser {
        if (index == -1) {
            return@runWithBrowser
        } else {
            seekTo(index, 0)
            play()
        }
    }

    override suspend fun skipToNext() = runWithBrowser {
        if (playMode == PlayMode.Shuffle) {
            sendCustomCommand(
                CustomCommand.SeekToNext.toSessionCommand(),
                Bundle.EMPTY
            )
        } else {
            seekToNext()
        }
    }

    override suspend fun skipToPrevious() = runWithBrowser {
        if (playMode == PlayMode.Shuffle) {
            sendCustomCommand(
                CustomCommand.SeekToPrevious.toSessionCommand(),
                Bundle.EMPTY
            )
        } else {
            seekToPrevious()
        }
    }

    override suspend fun seekTo(
        positionMs: Long
    ) = runWithBrowser {
        seekTo(positionMs)
    }

    override suspend fun updatePlaylist(
        playlist: List<LItem>,
        startIndex: Int,
        start: Boolean
    ) = withContext(Dispatchers.IO) {
        // 更新播放列表
        queue.replaceAll(index = startIndex, items = with(queue) { playlist.map { it.toPlayable() } })

        // 读取播放列表展平后的可播放元素
        val items = queue.expandedItems.value.list.map { it.item.toMediaItem() }

        runWithBrowser {
            setMediaItems(items, startIndex, 0)
            if (start) play()
        }
    }

    override suspend fun setPlaybackMode(
        mode: PlaybackMode
    ) = runWithBrowser {
        playMode = when (mode) {
            PlaybackMode.SEQUENTIAL -> PlayMode.ListRecycle
            PlaybackMode.LOOP -> PlayMode.ListRecycle
            PlaybackMode.SINGLE_LOOP -> PlayMode.RepeatOne
            PlaybackMode.SHUFFLE -> PlayMode.Shuffle
        }
    }

    override suspend fun setPauseWhenCompletion(cancel: Boolean) {
        pauseWhenCompletion = !cancel
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        this@MPlayerPlayback._isPlaying.value = isPlaying

        loopJob?.cancel()
        if (isPlaying) {
            loopJob = launch {
                while (isActive) {
                    withContext(Dispatchers.Main) {
                        LPlayerKV.historyPlayPosition.value = currentPosition()
                    }
                    delay(1000)
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onPlaybackStateChanged(playbackState: Int) {

    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        updateItems()

        if (pauseWhenCompletion) {
            browserInstance?.pause()
            pauseWhenCompletion = false
        }
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        _currentDuration.value = mediaMetadata.durationMs ?: browserInstance?.duration ?: 0L
        // TODO 此处获取到的duration仍然可能是上一首歌曲的时长
    }

    override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) {

    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        updateItems(timeline)
    }

    private fun updateItems(
        timeline: Timeline? = browserInstance?.currentTimeline,
        currentIndex: Int = browserInstance?.currentMediaItemIndex ?: 0
    ) {
        val items = timeline?.toMediaItems() ?: emptyList()
        val ids = items.map { it.mediaId }
        LPlayerKV.historyPlayId.value = ids.getOrNull(currentIndex) ?: ""

        queue.switchTo(currentIndex)
    }

    suspend fun runWithBrowser(
        block: suspend MediaBrowser.() -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val browser = browserFuture.await()
        withContext(Dispatchers.Main) {
            browser.block()
        }
    }

    /**
     * 差异更新媒体项，减少媒体项的更新，避免更新影响播放进度
     *
     * @param items 新的媒体项列表
     */
    private suspend fun diffUpdateMediaItems(items: List<LAudio>) = withContext(Dispatchers.IO) {
        val browser = browserFuture.runCatching { get() }.getOrNull() ?: return@withContext
        val currentIds = withContext(Dispatchers.Main) { browser.currentTimeline.toMediaItems().map { it.mediaId } }

        val mediaItems = items.map { it.toMediaItem() }
        val newIds = mediaItems.map { it.mediaId }
        val changes = MyersDiff<String>().computeDiff(source = currentIds, target = newIds)

        withContext(Dispatchers.Main) {
            changes.forEach { change ->
                when (change.deltaType) {
                    DeltaType.DELETE -> browser.removeMediaItems(change.startOriginal, change.endOriginal)

                    DeltaType.INSERT -> browser.addMediaItems(
                        change.startOriginal,
                        mediaItems.subList(change.startRevised, change.endRevised)
                    )

                    DeltaType.CHANGE -> browser.replaceMediaItems(
                        change.startOriginal,
                        change.endOriginal,
                        mediaItems.subList(change.startRevised, change.endRevised)
                    )

                    DeltaType.EQUAL -> {}
                }
            }
        }
    }
}

private fun Timeline.toMediaItems(): List<MediaItem> {
    return (0 until this.windowCount)
        .mapNotNull { this.getWindow(it, Timeline.Window()).mediaItem }
}

private fun Timeline.indexOf(mediaId: String): Int {
    return (0 until this.windowCount).firstOrNull {
        this.getWindow(it, Timeline.Window())
            .mediaItem.mediaId == mediaId
    } ?: -1
}
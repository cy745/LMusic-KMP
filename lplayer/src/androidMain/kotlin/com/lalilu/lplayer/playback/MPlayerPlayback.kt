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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import co.touchlab.kermit.Logger
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.PlatformMediaSource
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext

@Single
@OptIn(UnstableApi::class, ExperimentalCoroutinesApi::class)
class MPlayerPlayback(
    private val context: Context,
    private val audioRepository: AudioRepository,
    private val mediaSourceBindingRepository: MediaSourceBindingRepository,
    private val platformMediaSource: PlatformMediaSource,
    private val history: PlaybackHistory
) : CoroutineScope,
    Player.Listener,
    Playback,
    Runnable,
    PlaybackHistory by history {

    private val logger = Logger.withTag("MPlayerPlayback")
    private val browserQueueMutex = Mutex()
    private val timelineSyncLock = Any()
    private var timelineSyncGeneration = 0L
    override val coroutineContext: CoroutineContext = Dispatchers.IO
    private val sessionToken by lazy {
        SessionToken(context, ComponentName(context, MService::class.java))
    }

    override val queue: PlayableQueue = PlayableQueueImpl()
    private var browserInstance: MediaBrowser? = null
    private val browserFuture by lazy {
        MediaBrowser
            .Builder(context, sessionToken)
            .buildAsync()
    }

    var pauseWhenCompletion: Boolean by mutableStateOf(false)
        private set

    // Protected mutable state flows
    private val _isPlaying = MutableStateFlow(false)
    private val _errors = MutableSharedFlow<Throwable>()
    private val _currentDuration = MutableStateFlow(0L)
    private val _currentBufferedPosition = MutableStateFlow(0L)
    private val _playbackMode = MutableStateFlow(PlaybackMode.SEQUENTIAL)
    private val contentPreparation = ContentReadyPreparationCoordinator(
        scope = this,
        sourceOf = { audio ->
            platformMediaSource.sources.firstOrNull { it.name == audio.mediaSourceName }
        },
        onReady = { audio, playWhenReady ->
            withContext(Dispatchers.Main) {
                val browser = browserInstance ?: return@withContext
                if (browser.currentMediaItem?.mediaId != audio.id) return@withContext
                browser.playWhenReady = playWhenReady
                browser.prepare()
            }
        },
        onSourceMissing = { audio ->
            _errors.emit(IllegalStateException("MediaSource '${audio.mediaSourceName}' not found"))
        },
    )

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
        startQueueMetadataRefresh(queue, audioRepository)
        startBrowserQueueSync()

        // 历史恢复
        val snapshot = restoreFromHistory()
        val restorer = snapshot?.let {
            HistoryQueueRestorer(
                snapshot = it,
                repository = audioRepository,
                restoreSettled = mediaSourceBindingRepository.observeHistoryRestoreSettled(),
            )
        }
        restorer?.start(
            scope = this,
            queue = queue,
            onCurrentResolved = ::onQueueRestored,
        )

        // 自动录制
        startRecording(this, restorer?.state)
    }

    override fun currentPosition(): Long {
        return runCatching { if (browserFuture.isDone) browserFuture.get()?.currentPosition else null }
            .getOrNull() ?: 0L
    }

    override suspend fun play() {
        val current = queue.currentItem()
        if (current != null && !isContentReady(current)) {
            contentPreparation.request(current, playWhenReady = true)
            return
        }
        contentPreparation.updatePlayIntent(current?.id, playWhenReady = true)
        runWithBrowser { play() }
    }

    override suspend fun pause() {
        contentPreparation.updatePlayIntent(queue.currentItem()?.id, playWhenReady = false)
        runWithBrowser { pause() }
    }
    override suspend fun togglePlayPause() {
        if (_isPlaying.value || contentPreparation.hasPendingPlayIntent()) pause() else play()
    }

    override suspend fun stop() {
        contentPreparation.cancel()
        runWithBrowser { stop() }
    }
    override suspend fun seekTo(positionMs: Long) = runWithBrowser { seekTo(positionMs) }

    override suspend fun skipTo(index: Int, start: Boolean) {
        if (index == -1) return
        val queueSnapshot = queue.stateSnapshot()
        val target = queueSnapshot.list.getOrNull(index)
        val targetReady = target?.let(::isContentReady) != false

        // Queue changes are normally mirrored to Media3 by a Flow collector. A caller can update
        // the app queue and immediately skip to a newly inserted index before that collector runs,
        // in which case Media3 would seek to the item that previously occupied the index. Make the
        // mirror deterministic before resolving the index in the platform player.
        diffUpdateMediaItems(queueSnapshot.list)

        runWithBrowser {
            seekTo(index, 0)
            if (start && targetReady) play()
        }

        if (start && target != null && !targetReady) {
            contentPreparation.request(target, playWhenReady = true)
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

    override suspend fun updatePlaylist(
        playlist: List<LAudio>,
        startIndex: Int,
        start: Boolean
    ) {
        val mediaItems = playlist.map { it.toMediaItem() }
        val target = playlist.getOrNull(startIndex)
        val targetReady = target?.let(::isContentReady) != false

        runWithBrowser {
            if (start && !targetReady) playWhenReady = false
            setMediaItems(mediaItems, startIndex, 0)
            if (start && targetReady) play()
        }

        if (start && target != null && !targetReady) {
            contentPreparation.request(target, playWhenReady = true)
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


    override suspend fun onQueueRestored(snapshot: PlaybackHistory.HistorySnapshot) {
        val restored = queue.stateSnapshot()
        val mediaIds = restored.list.map { it.toMediaItem() }
        val browser = browserInstance ?: return

        browserQueueMutex.withLock {
            withContext(Dispatchers.Main) {
                // 先恢复精确的队列、current 和 position，但等目标来源 Ready 后再真正打开媒体。
                browser.playWhenReady = false
                browser.setMediaItems(mediaIds, restored.index, snapshot.position)
            }
        }

        restored.currentItem()?.let { current ->
            contentPreparation.request(
                audio = current,
                playWhenReady = LPlayerKV.autoPlayWhenRestart.value,
            )
        }

    }

    /**
     * browser 建立后立即镜像应用队列。这样即使历史 current 暂时缺失，已经解析出的其他歌曲也能
     * 先进入播放器；[onQueueRestored] 只负责最终应用准确的 current 和 position。
     */
    private fun startBrowserQueueSync() {
        queue.expandedItems
            .filter {
                it.updateReason !is QueueUpdateReason.Sync &&
                    it.updateReason !is QueueUpdateReason.Unknown
            }
            .onEach { (list, _) -> diffUpdateMediaItems(list) }
            .launchIn(this@MPlayerPlayback)
    }


    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _isPlaying.value = isPlaying
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        contentPreparation.cancelIfCurrentChanged(mediaItem?.mediaId)
        updateItems()

        if (pauseWhenCompletion) {
            browserInstance?.pause()
            pauseWhenCompletion = false
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        val mediaId = browserInstance?.currentMediaItem?.mediaId
        val shouldResume = browserInstance?.playWhenReady == true
        if (mediaId == null) {
            _errors.tryEmit(error)
            return
        }

        launch {
            val audio = audioRepository.getAudio(mediaId).first()
            if (audio != null && !isContentReady(audio)) {
                // Media3 的有限等待已经失败：继续在可取消协程中等待该来源，Ready 后确定性重试。
                contentPreparation.request(audio, playWhenReady = shouldResume)
            } else {
                _errors.emit(error)
            }
        }
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        _currentDuration.value = mediaMetadata.durationMs ?: browserInstance?.duration ?: 0L
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
        val mediaItems = timeline?.toMediaItems() ?: emptyList()
        val generation = synchronized(timelineSyncLock) { ++timelineSyncGeneration }

        // 同步播放列表变化到playableQueue
        launch {
            val ids = mediaItems.map { it.mediaId }
            val items = audioRepository.getAudios(ids).first().map { it }

            // Timeline 在一次差量更新中可能连续回调。数据库查询较慢时，只让最后一份快照回写，
            // 避免旧的中间态晚到并覆盖完整队列。
            queue.update(
                updateReason = QueueUpdateReason.Sync,
                predicate = {
                    synchronized(timelineSyncLock) { generation == timelineSyncGeneration }
                },
            ) {
                replaceAll(items = items, index = currentIndex)
            }
        }
    }

    private suspend fun diffUpdateMediaItems(items: List<LAudio>) = browserQueueMutex.withLock {
        withContext(Dispatchers.IO) {
            val browser = browserFuture.runCatching { get() }.getOrNull() ?: return@withContext
            val currentIds = withContext(Dispatchers.Main) {
                browser.currentTimeline.toMediaItems().map { it.mediaId }
            }

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

    private fun isContentReady(audio: LAudio): Boolean = platformMediaSource.sources
        .firstOrNull { it.name == audio.mediaSourceName }
        ?.contentState
        ?.value
        ?.isReady == true

    private suspend fun runWithBrowser(
        block: suspend MediaBrowser.() -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val browser = browserFuture.await()
        withContext(Dispatchers.Main) {
            browser.block()
        }
    }
}

private fun Timeline.toMediaItems(): List<MediaItem> {
    return (0 until this.windowCount)
        .mapNotNull { this.getWindow(it, Timeline.Window()).mediaItem }
}

package com.lalilu.lplayer.playback

import co.touchlab.kermit.Logger
import com.lalilu.lplayer.action.launchPlayerAction
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.lmedia.domain.model.PlaybackFailure
import com.lalilu.lmedia.domain.repository.PlaybackFailureRepository
import com.lalilu.lmedia.domain.model.mediaKey
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.source.resolveMediaData
import com.lalilu.lplayer.NativeExtractor
import com.lalilu.lplayer.menu.MacOSMenu
import com.lalilu.lplayer.notification.MacOSNotification
import com.lalilu.lplayer.player.VLCPlayer
import com.lalilu.lplayer.player.VLCPlayerLoader
import com.lalilu.lplayer.playback.PlaybackEngine
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlin.time.TimeSource
import org.koin.core.annotation.Single
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter

@Single(binds = [Playback::class])
fun provideVLCPlayback(
    audioRepository: AudioRepository,
    history: PlaybackHistory,
    platformMediaSource: PlatformMediaSource,
    dataTracker: IPlaybackDataTracker,
    playbackFailureRepository: PlaybackFailureRepository,
): VLCPlayback = VLCPlayback(audioRepository, history, platformMediaSource, dataTracker, playbackFailureRepository)

class VLCPlayback internal constructor(
    audioRepository: AudioRepository,
    private val history: PlaybackHistory,
    override val platformMediaSource: PlatformMediaSource,
    private val dataTracker: IPlaybackDataTracker,
    private val playbackFailureRepository: PlaybackFailureRepository,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val nativePlayerProvider: suspend () -> MediaPlayer = {
        VLCPlayerLoader.initialize().join()
        checkNotNull(VLCPlayer.getPlayer()) { "VLC player initialization failed" }
    },
    private val installPlatformControls: (VLCPlayback) -> Unit = { playback ->
        if (NativeExtractor.isMac()) {
            MacOSMenu(playback)
            MacOSNotification(playback)
        }
    },
) : AbstractPlayback(coroutineScope = scope, history = history, audioRepository = audioRepository), HistoryPositionProvider {
    @Volatile private var playerInstance: MediaPlayer? = null
    private var loadedMediaKey: MediaKey? = null
    private val logger = Logger.withTag("VLCPlayback")
    private val commands = LatestPlaybackCommand()
    private val failureWrites = PlaybackFailureWrites(playbackFailureRepository)
    private var successfulPlaybackWatch: Job? = null
    // Serialize unsuspended command sections without occupying the Compose UI thread.
    // LatestPlaybackCommand handles cancellation across suspension points.
    private val commandDispatcher = Dispatchers.IO.limitedParallelism(1)

    override suspend fun play(): Unit = withContext(commandDispatcher) {
        claimHistoryPlaybackControl()
        commands.run { playInternal() }
    }

    override suspend fun pause(): Unit = withContext(commandDispatcher) {
        claimHistoryPlaybackControl()
        commands.run { pauseInternal() }
    }

    override suspend fun stop(): Unit = withContext(commandDispatcher) {
        claimHistoryPlaybackControl()
        commands.run { stopInternal() }
    }

    override suspend fun skipTo(index: Int, start: Boolean): Unit = withContext(commandDispatcher) {
        claimHistoryPlaybackControl()
        commands.run { selectInternal(index, start) }
    }

    override suspend fun skipToNext(): Unit = withContext(PlaybackNavigationContext(PlaybackDirection.Forward)) {
        super.skipToNext()
    }

    override suspend fun skipToPrevious(): Unit = withContext(PlaybackNavigationContext(PlaybackDirection.Backward)) {
        super.skipToPrevious()
    }

    override suspend fun seekTo(positionMs: Long): Unit = withContext(commandDispatcher) {
        claimHistoryPlaybackControl()
        commands.run {
            check(loadedMediaKey != null && loadedMediaKey == queue.currentItem()?.mediaKey) {
                "Cannot seek before the selected media is loaded"
            }
            seekInternal(positionMs)
        }
    }

    override fun createEngines(): List<PlaybackEngine> = emptyList()

    val player: MediaPlayer
        get() = playerInstance ?: throw Exception("Player Not Initialized")

    init {
        launch(commandDispatcher) {
            try {
                playerInstance = nativePlayerProvider().also { bindPlayer(it) }
                startHistoryPlayback()
                installPlatformControls(this@VLCPlayback)
            } catch (failure: Exception) {
                reportPlaybackCommandFailure(failure) {
                    logger.e(messageString = "VLC initialization failed", throwable = it)
                    emitError(it)
                }
            }
        }
    }

    private suspend fun playItem(item: LAudio, start: Boolean, position: Long = 0) {
        successfulPlaybackWatch?.cancel()
        successfulPlaybackWatch = null
        loadedMediaKey = null
        val successTicket = failureWrites.register(item.playbackId)
        val data = platformMediaSource.resolveMediaData(item)
        currentCoroutineContext().ensureActive()
        prepareVlcMedia(player, data, position)
        loadedMediaKey = item.mediaKey
        watchSuccessfulPlayback(item.mediaKey, successTicket)
        if (start) {
            player.controls().play()
        }
    }

    /** A prepared/paused input is not a successful retry. Confirm actual native progress. */
    private fun watchSuccessfulPlayback(key: MediaKey, ticket: PlaybackFailureWrites.Ticket) {
        successfulPlaybackWatch = launch(commandDispatcher) {
            var previousPosition: Long? = null
            while (isActive && loadedMediaKey == key) {
                delay(250)
                if (commands.isRunning || loadedMediaKey != key || queue.currentItem()?.mediaKey != key ||
                    !player.status().isPlaying) {
                    previousPosition = null
                    continue
                }
                val position = player.status().time()
                val previous = previousPosition
                previousPosition = position
                if (previous != null && previous >= 0 && position > previous) {
                    val result = failureWrites.apply(ticket, null)
                    if (result.isSuccess) return@launch
                    result.onFailure { logger.e(it) { "Could not clear VLC playback failure" } }
                }
            }
        }
    }

    override suspend fun restoreLoadedItem(audio: LAudio, position: Long, start: Boolean): Unit =
        withContext(commandDispatcher) {
            commands.run { restoreInternal(audio, position, start) }
        }

    private suspend fun restoreInternal(audio: LAudio, position: Long, start: Boolean) {
        try {
            playItem(audio, start, position)
        } catch (failure: Exception) {
            // 历史恢复失败必须继续上抛，恢复器据此保留 Pending.failure 并允许重试。
            recordLoadFailure(audio, failure)
            throw failure
        }
    }

    private suspend fun playInternal() {
        try {
            if (player.media().isValid && loadedMediaKey != null && loadedMediaKey == queue.currentItem()?.mediaKey) {
                player.controls().play()
                _isPlaying.value = true
            } else {
                check(queue.currentItem() != null) { "No media to play" }
                selectInternal(queue.stateSnapshot().index, true)
            }
        } catch (e: Exception) {
            reportPlaybackCommandFailure(e) {
                logger.e(tag = "VLCPlayback", messageString = "${e.message}", throwable = e)
                emitError(e)
            }
        }
    }

    private suspend fun pauseInternal() {
        try {
            player.controls().setPause(true)
            _isPlaying.value = false
        } catch (e: Exception) {
            reportPlaybackCommandFailure(e) {
                logger.e(tag = "VLCPlayback", messageString = "${e.message}", throwable = e)
                emitError(e)
            }
        }
    }

    override suspend fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    private suspend fun stopInternal() {
        successfulPlaybackWatch?.cancel()
        successfulPlaybackWatch = null
        try {
            player.controls().stop()
            _isPlaying.value = false
            loadedMediaKey = null
        } catch (e: Exception) {
            reportPlaybackCommandFailure(e) {
                logger.e(tag = "VLCPlayback", messageString = "${e.message}", throwable = e)
                emitError(e)
            }
        }
    }

    private suspend fun selectInternal(index: Int, start: Boolean) {
        val direction = currentCoroutineContext()[PlaybackNavigationContext]?.direction ?: PlaybackDirection.Forward
        val navigationStartedAt = TimeSource.Monotonic.markNow()
        navigateWithFailureFallback(
            traversal = PlaybackFailureTraversal(),
            initialIndex = index,
            direction = direction,
            playbackMode = { playbackMode.value },
            candidates = { queue.stateSnapshot().list },
            playableSlots = { list -> playableSlots(list) },
            // 只有"用户请求了播放、且这次是真正的加载"才允许跳过；已经加载成功后的失败不连跳。
            skipPolicy = { item -> start && !isLoadedItem(item) },
            recordFailure = { item, failure -> recordLoadFailure(item, failure) },
            stop = { stopInternal() },
            attempt = { target, _ -> selectOneInternal(target, start) },
            outOfBudget = { navigationStartedAt.elapsedNow() > DefaultSkipNavigationBudget },
        )
    }

    /** 可以尝试的槽位：可用、来源已就绪，且没有已记录的失败。一次跳过搜索只读一次失败表。 */
    private suspend fun playableSlots(list: List<LAudio>): Set<Int> {
        val known = runCatching { playbackFailureRepository.failures.first() }
            .onFailure { logger.e(it) { "Could not read recorded playback failures; skipping without them" } }
            .getOrDefault(emptyMap())
        return playablePlaybackSlots(list, known.keys) { candidate ->
            platformMediaSource.findEnabledSource(candidate.mediaSourceName)
                ?.contentState?.value?.isReady == true
        }
    }

    private fun isLoadedItem(item: LAudio): Boolean = loadedMediaKey == item.mediaKey &&
        // 未初始化的播放器不能在这里抛错：该判定发生在尝试之前，抛出去会绕过失败处理。
        runCatching { playerInstance?.media()?.isValid == true }.getOrDefault(false)

    /** 来源还在加载时，加载失败不是这首歌的问题。取消从不记账。 */
    private suspend fun recordLoadFailure(item: LAudio, failure: Exception) {
        if (failure is CancellationException) return
        if (platformMediaSource.findEnabledSource(item.mediaSourceName)?.contentState?.value?.isReady != true) return
        failureWrites.apply(failureWrites.register(item.playbackId), PlaybackFailure(
            classifyVlcLoadFailure(failure), System.currentTimeMillis(),
        )).onFailure { logger.e(it) { "Could not persist VLC load failure" } }
    }

    private suspend fun selectOneInternal(index: Int, start: Boolean) {
        logger.i { "skipTo $index start=$start" }
        try {
            val state = queue.stateSnapshot()
            val targetItem = state.list.getOrNull(index)
                ?: throw Exception("Invalid index")
            if (loadedMediaKey == targetItem.mediaKey && player.media().isValid) {
                seekInternal(0)
                queue.update { switchTo(index) }
                prepareSurpriseNext()
                if (start) playInternal() else pauseInternal()
            } else {
                val oldItem = state.currentItem()
                playItem(targetItem, start)
                queue.update { switchTo(index) }
                prepareSurpriseNext()

                dataTracker.onMediaItemTransition(
                    mediaId = targetItem.playbackId,
                    title = targetItem.title,
                    isRepeating = oldItem?.mediaKey == targetItem.mediaKey,
                    isNormalTransition = oldItem?.mediaKey != targetItem.mediaKey
                )
            }
        } catch (e: Exception) {
            reportPlaybackCommandFailure(e) {
                logger.e(tag = "VLCPlayback", messageString = "${e.message}", throwable = e)
                emitError(e)
            }
        }
    }

    private suspend fun seekInternal(positionMs: Long) {
        try {
            player.controls().setTime(positionMs)
        } catch (e: Exception) {
            reportPlaybackCommandFailure(e) {
                logger.e(tag = "VLCPlayback", messageString = "${e.message}", throwable = e)
                emitError(e)
            }
        }
    }

    // Native time is also authoritative while paused, stopped or seeking. Do not
    // extrapolate an old callback: a late event could restart that clock after stop.
    override fun currentPosition(): Long = playerInstance?.status()?.time()?.coerceAtLeast(0L) ?: 0L

    override suspend fun historyPosition(expectedQueue: QueueState): Long? = withContext(commandDispatcher) {
        val native = playerInstance ?: return@withContext null
        if (commands.isRunning || queue.stateSnapshot() !== expectedQueue) return@withContext null
        val state = native.status().state()
        if (state == uk.co.caprica.vlcj.player.base.State.STOPPED && loadedMediaKey == null) {
            return@withContext 0L
        }
        if (loadedMediaKey == null || loadedMediaKey != expectedQueue.currentItem()?.mediaKey) return@withContext null
        if (state != uk.co.caprica.vlcj.player.base.State.PAUSED &&
            state != uk.co.caprica.vlcj.player.base.State.PLAYING) return@withContext null
        native.status().time().takeIf { it >= 0 }
    }

    private fun bindPlayer(player: MediaPlayer) {
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer?) {
                logger.i(tag = "VLCPlayback_player", messageString = "playing")
                _isPlaying.value = true
                dataTracker.onIsPlayingChanged(true)
            }

            override fun paused(mediaPlayer: MediaPlayer?) {
                logger.i(tag = "VLCPlayback_player", messageString = "pausing")
                _isPlaying.value = false
                dataTracker.onIsPlayingChanged(false)
            }

            override fun error(mediaPlayer: MediaPlayer?) {
                val failure = IllegalStateException("VLC failed to load media")
                emitError(failure)
            }

            override fun finished(mediaPlayer: MediaPlayer?) {
                logger.i(tag = "VLCPlayback_player", messageString = "finished")
                if (_pauseWhenCompletion) {
                    _pauseWhenCompletion = false
                    _isPlaying.value = false
                } else {
                    launchPlayerAction { skipToNext() }
                }
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer?, newLength: Long) {
                logger.i(tag = "VLCPlayback_player", messageString = "timeChanged, new length: $newLength")
                _currentDuration.value = newLength
            }
        })
    }
}

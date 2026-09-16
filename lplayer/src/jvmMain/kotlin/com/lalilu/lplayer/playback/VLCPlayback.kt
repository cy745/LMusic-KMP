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
import uk.co.caprica.vlcj.player.base.State
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

    /** 用户是否明确要求过播放当前这首歌；播放中途/加载期出错时只有它仍成立才自动继续下一首。 */
    private var playRequestedFor: MediaKey? = null

    /** 观察者确认已就绪的媒体；用于判断 seek 能否直接作用于原生。 */
    private var preparedKey: MediaKey? = null

    /** 原生还没就绪时收到的定位请求：就绪后由观察者补上，调用方不必等待预备。 */
    private var pendingSeek: Long? = null
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
        preparedKey = null
        pendingSeek = null
        val data = platformMediaSource.resolveMediaData(item)
        currentCoroutineContext().ensureActive()
        prepareVlcMedia(player, data, position)
        preparedKey = item.mediaKey
        loadedMediaKey = item.mediaKey
        // 恢复路径已经在命令内完成定位与播放意图，观察者只负责清除/记账。
        playRequestedFor = null
        watchLoadedItem(item, successTicket, requestPlay = false)
        if (start) {
            player.controls().play()
        }
    }

    /**
     * 常驻观察者：就绪后按需真正播放；坏内容/超时按歌曲记账并（在用户要求播放时）继续下一首；
     * 真实位置推进后才清除失败记录。一次加载一个观察者，换歌即取消，因此迟到的结果无法劫持新歌。
     */
    private fun watchLoadedItem(item: LAudio, ticket: PlaybackFailureWrites.Ticket, requestPlay: Boolean) {
        val key = item.mediaKey
        successfulPlaybackWatch?.cancel()
        successfulPlaybackWatch = launch(commandDispatcher) {
            val watch = LoadFailureWatch()
            val startedAt = TimeSource.Monotonic.markNow()
            var previousPosition: Long? = null
            var prepared = false
            var playApplied = !requestPlay
            while (isActive && loadedMediaKey == key) {
                delay(250)
                if (commands.isRunning || loadedMediaKey != key || queue.currentItem()?.mediaKey != key) {
                    previousPosition = null
                    continue
                }
                val state = player.status().state()
                // 实测：坏内容/不存在的文件会在从未进入 PAUSED（音轨 -1）的情况下直接 ENDED。
                if (state == State.ERROR || (state == State.ENDED && !prepared)) {
                    handleStalledFailure(item, ticket)
                    return@launch
                }
                if (!prepared) {
                    if (state == State.PAUSED && player.audio().track() >= 0) {
                        prepared = true
                        preparedKey = key
                        // 就绪前收到的定位请求在这里补上：调用方无需等待预备。
                        pendingSeek?.let { target ->
                            pendingSeek = null
                            player.controls().setTime(target)
                        }
                    } else if (startedAt.elapsedNow() > DEFAULT_PREPARATION_TIMEOUT) {
                        handleStalledFailure(item, ticket)
                        return@launch
                    } else {
                        previousPosition = null
                        continue
                    }
                }
                if (!playApplied) {
                    // 用户在就绪之前暂停/停止了：不能把"就绪后补播"再执行一遍。
                    if (playRequestedFor != key) {
                        playApplied = true
                        previousPosition = null
                        continue
                    }
                    player.controls().play()
                    playApplied = true
                    previousPosition = null
                    continue
                }
                if (!player.status().isPlaying) {
                    previousPosition = null
                    continue
                }
                val position = player.status().time()
                val previous = previousPosition
                previousPosition = position
                if (previous == null || previous < 0 || position <= previous) continue
                if (watch.onPositionAdvanced()) {
                    failureWrites.apply(ticket, null).onFailure {
                        logger.e(it) { "Could not clear VLC playback failure" }
                    }
                }
            }
        }
    }

    /** 加载没能就绪：按来源就绪记一次账；用户要求过播放且位置停住时继续沿方向找下一首。 */
    private suspend fun handleStalledFailure(item: LAudio, ticket: PlaybackFailureWrites.Ticket) {
        val recorded = recordLoadFailure(item, IllegalStateException("VLC did not prepare the audio"))
        if (!recorded) return
        if (!shouldNavigateAfterStalledFailure(
                failedKey = item.mediaKey,
                currentItemKey = queue.currentItem()?.mediaKey,
                loadedKey = loadedMediaKey,
                playRequestedKey = playRequestedFor,
                positionAdvanced = false,
            )) return
        playRequestedFor = null
        navigateAfterStalledFailure(item.mediaKey)
    }

    /** 用独立协程发起：观察者会随换歌被取消，不能让它把这次导航一起带走。 */
    private fun navigateAfterStalledFailure(key: MediaKey) {
        launch(commandDispatcher) {
            if (loadedMediaKey != key || queue.currentItem()?.mediaKey != key) return@launch
            try {
                skipToNext()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                logger.e(failure) { "Could not continue playback after a stalled failure" }
            }
        }
    }

    /**
     * 发起加载并交给观察者；**不等待原生预备**。选曲命令位于队列编辑边界内，
     * 在这里等待会把点歌与队列编辑一起阻塞到原生上限。
     */
    private suspend fun startItem(item: LAudio, start: Boolean) {
        successfulPlaybackWatch?.cancel()
        successfulPlaybackWatch = null
        preparedKey = null
        pendingSeek = null
        playRequestedFor = item.mediaKey.takeIf { start }
        try {
            val data = platformMediaSource.resolveMediaData(item)
            currentCoroutineContext().ensureActive()
            startVlcMedia(player, data)
        } catch (failure: Exception) {
            playRequestedFor = null
            throw failure
        }
        loadedMediaKey = item.mediaKey
        watchLoadedItem(item, failureWrites.register(item.playbackId), requestPlay = start)
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
                playRequestedFor = loadedMediaKey
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
        playRequestedFor = null
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
        playRequestedFor = null
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

    /** 来源还在加载时，加载失败不是这首歌的问题。取消从不记账。返回是否真的写入了记录。 */
    private suspend fun recordLoadFailure(item: LAudio, failure: Exception): Boolean {
        if (failure is CancellationException) return false
        if (platformMediaSource.findEnabledSource(item.mediaSourceName)?.contentState?.value?.isReady != true) return false
        failureWrites.apply(failureWrites.register(item.playbackId), PlaybackFailure(
            classifyVlcLoadFailure(failure), System.currentTimeMillis(),
        )).onFailure { logger.e(it) { "Could not persist VLC load failure" } }
        return true
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
                playRequestedFor = targetItem.mediaKey.takeIf { start }
                if (start) playInternal() else pauseInternal()
                if (successfulPlaybackWatch?.isActive != true) {
                    watchLoadedItem(targetItem, failureWrites.register(targetItem.playbackId), requestPlay = start)
                }
            } else {
                val oldItem = state.currentItem()
                startItem(targetItem, start)
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
        if (preparedKey != loadedMediaKey) {
            // 原生还在打开：直接 setTime 会被忽略甚至打断打开流程，交给观察者就绪后补上。
            pendingSeek = positionMs
            return
        }
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

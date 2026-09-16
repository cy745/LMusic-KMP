package com.lalilu.lplayer.playback

import co.touchlab.kermit.Logger
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.repository.PlaybackFailureRepository
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.PlaybackFailure
import com.lalilu.lmedia.domain.model.mediaKey
import com.lalilu.lplayer.extensions.VolumeFadeHelper
import com.lalilu.lplayer.helper.AudioSessionHelper
import com.lalilu.lplayer.notifacation.NowPlayingInfoNotification
import com.lalilu.lplayer.notifacation.RemoteCommandHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.ExperimentalTime
import kotlin.math.abs
import org.koin.core.annotation.Single
import org.koin.core.component.KoinComponent

/**
 * iOS 平台播放实现。
 *
 * 通过 [PlaybackEngineRouter] 在 [AVPlayerEngine]（Url）和
 * [AVAudioPlayerEngine]（Bytes）之间按类型匹配切换。
 * 平台基础设施（NowPlaying、RemoteCommand、AudioSession）在此层管理。
 */
@OptIn(ExperimentalTime::class)
@Single(binds = [Playback::class])
class AVPlayerPlayback(
    history: PlaybackHistory,
    audioRepository: AudioRepository,
    private val playbackFailureRepository: PlaybackFailureRepository
) : AbstractPlayback(history = history, audioRepository = audioRepository), KoinComponent {

    companion object {
        const val TAG = "AVPlayerPlayback"
    }

    private val logger = Logger.withTag(TAG)
    private var loadedMediaKey: MediaKey? = null
    private val commands = LatestPlaybackCommand()
    private val failureWrites = PlaybackFailureWrites(playbackFailureRepository)

    /** 只观察当前已加载项；换歌或停止时取消，避免把旧结果写回新歌曲。 */
    private var loadedWatch: Job? = null

    /**
     * 用户是否明确要求过播放当前这首歌。播放中途出错时只有它仍成立才自动跳下一首，
     * 避免"用户已经暂停/停止，播放器却自己把歌跳走"。
     */
    private var playRequestedFor: MediaKey? = null

    override suspend fun play(): Unit = withContext(Dispatchers.Main) {
        claimHistoryPlaybackControl()
        commands.run { playInternal() }
    }

    override suspend fun pause(): Unit = withContext(Dispatchers.Main) {
        claimHistoryPlaybackControl()
        commands.run { pauseInternal() }
    }

    override suspend fun stop(): Unit = withContext(Dispatchers.Main) {
        claimHistoryPlaybackControl()
        commands.run { stopInternal() }
    }

    override suspend fun skipToNext(): Unit = withContext(PlaybackNavigationContext(PlaybackDirection.Forward)) {
        super.skipToNext()
    }

    override suspend fun skipToPrevious(): Unit = withContext(PlaybackNavigationContext(PlaybackDirection.Backward)) {
        super.skipToPrevious()
    }

    override suspend fun skipTo(index: Int, start: Boolean): Unit = withContext(Dispatchers.Main) {
        claimHistoryPlaybackControl()
        commands.run { selectInternal(index, start) }
    }

    override suspend fun seekTo(positionMs: Long): Unit = withContext(Dispatchers.Main) {
        claimHistoryPlaybackControl()
        commands.run {
            check(loadedMediaKey != null && loadedMediaKey == queue.currentItem()?.mediaKey) {
                "Cannot seek before the selected media is loaded"
            }
            seekInternal(positionMs)
        }
    }

    override fun createEngines(): List<PlaybackEngine> = listOf(
        MusicKitEngine(),
        AVAudioPlayerEngine(),
        AVPlayerEngine(),
    )

    private var volumeFadeHelper = VolumeFadeHelper(
        onSetVolume = { v ->
            when (val engine = activeEngine) {
                is AVPlayerEngine -> engine.setVolume(v)
                is AVAudioPlayerEngine -> engine.setVolume(v)
            }
        },
        // MusicKitEngine 不支持音量控制，跳过渐变直接播放/暂停
        fadeEnabled = { activeEngine !is MusicKitEngine }
    )

    init {
        // 给每个 Engine 绑定 onEvent 回调（覆盖父类的默认绑定，加入日志）
        engineRouter.allEngines.forEach { engine ->
            val original = engine.onEvent
            engine.onEvent = { event ->
                when (event) {
                    is PlaybackEngineEvent.Completion -> {
                        logger.i { "Engine completion: ${engine::class.simpleName}" }
                        original?.invoke(event)
                    }

                    is PlaybackEngineEvent.Error -> {
                        logger.e(
                            tag = TAG,
                            messageString = "Engine error: ${engine::class.simpleName}",
                            throwable = event.throwable
                        )
                        original?.invoke(event)
                    }
                }
            }
        }

        NowPlayingInfoNotification.bindPlayback(this)
        RemoteCommandHandler.bindPlayback(this)
        if (AudioSessionHelper.setUpAudioSession()) {
            // MusicKit 启动播放时会触发 AudioSession 中断通知，导致误暂停。
            // 自定义中断处理器：仅当非 MusicKitEngine 时 pause。
            AudioSessionHelper.bindPlayback(this, onInterruptionBegan = {
                if (activeEngine !is MusicKitEngine) {
                    pause()
                }
            })
        }
        startHistoryPlayback()
    }

    override suspend fun restoreLoadedItem(audio: LAudio, position: Long, start: Boolean): Unit =
        withContext(Dispatchers.Main) {
            commands.run { restoreInternal(audio, position, start) }
        }

    private suspend fun restoreInternal(audio: LAudio, position: Long, start: Boolean): Unit =
        withContext(Dispatchers.Main) {
            loadedWatch?.cancel()
            loadedWatch = null
            val successTicket = failureWrites.register(audio.playbackId)
            try {
                val data = resolveMediaData(audio)
                ensureActive()
                val engine = engineRouter.selectEngine(data, audio) ?: throw NoEngineFoundException(data, audio)
                if (engine !== activeEngine) {
                    activeEngine?.release()
                    activeEngine = engine
                }
                loadedMediaKey = null
                engine.load(data, audio)
                ensureActive()
                val ready = withTimeout(30_000) { engine.state.first { !it.isLoading } }
                check(ready.error == null) { ready.error ?: "History media load failed" }
                val target = if (ready.duration > 0) position.coerceIn(0, ready.duration) else position.coerceAtLeast(0)
                engine.seekTo(target)
                withTimeout(5_000) {
                    while (abs(engine.currentPosition() - target) > 100) delay(20)
                }
                loadedMediaKey = audio.mediaKey
                if (start) engine.play() else engine.pause()
                watchLoadedItem(engine, audio.mediaKey, successTicket)
            } catch (failure: Exception) {
                // 历史恢复失败必须继续上抛，恢复器据此保留 Pending.failure 并允许重试。
                persistLoadFailure(audio, successTicket, failure)
                throw failure
            }
        }

    /**
     * 加载成功不等于播放成功：只有真实位置推进才清除失败记录。
     * 引擎在加载后才报错（AVPlayer 的 status=Failed）时，命令路径看不到异常，由这里记账。
     *
     * 观察器常驻到该媒体被替换/停止为止：记账后不能退出，否则用户用播放键恢复播放成功后
     * 失败记录再也清不掉（Android 是事件式清除，Desktop 的 watcher 同样常驻）。
     * 记账与清除共用同一个 [ticket]，任何一方重新注册都会让另一次写入被判为过期。
     */
    private fun watchLoadedItem(engine: PlaybackEngine, key: MediaKey, ticket: PlaybackFailureWrites.Ticket) {
        loadedWatch?.cancel()
        loadedWatch = launch(Dispatchers.Main) {
            val watch = LoadFailureWatch()
            var previousPosition: Long? = null
            while (isActive) {
                delay(250)
                if (engine !== activeEngine || loadedMediaKey != key) return@launch
                try {
                    val state = engine.state.value
                    val audio = queue.currentItem()
                    if (watch.onStateError(state.error)) {
                        persistLoadFailure(audio, ticket, IllegalStateException(state.error))
                    }
                    val position = engine.currentPosition()
                    val previous = previousPosition
                    previousPosition = position
                    if (state.error != null) {
                        // 连续两次采样之间位置没有前进，说明这次错误真的让播放停住了。
                        if (shouldNavigateAfterStalledFailure(
                                failedKey = key,
                                currentItemKey = audio?.mediaKey,
                                loadedKey = loadedMediaKey,
                                playRequestedKey = playRequestedFor,
                                positionAdvanced = previous == null || position > previous,
                            )) {
                            playRequestedFor = null
                            navigateAfterStalledFailure(engine, key)
                            return@launch
                        }
                        continue
                    }
                    if (audio?.mediaKey != key || !state.isPlaying) {
                        previousPosition = null
                        continue
                    }
                    if (previous == null || position <= previous) continue
                    if (watch.onPositionAdvanced()) {
                        failureWrites.apply(ticket, null).onFailure {
                            logger.e(it) { "Could not clear iOS playback failure" }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    // 监视器自身的失败不能变成未处理异常（引擎释放后查询原生状态可能抛错）。
                    logger.e(failure) { "iOS playback failure watcher stopped" }
                    return@launch
                }
            }
        }
    }

    /**
     * 来源本身还在加载/停用时，加载失败不是这首歌的问题。取消从不记账。
     * [expectedPlaybackId] 必须与 [ticket] 同属一次加载，否则清除会被自己的写入判为过期。
     */
    private suspend fun persistLoadFailure(
        audio: LAudio?,
        ticket: PlaybackFailureWrites.Ticket,
        error: Throwable,
    ) {
        if (!shouldRecordIosLoadFailure(
                playbackId = audio?.playbackId,
                ticketId = ticket.id,
                sourceReady = audio != null && isSourceReady(audio),
                error = error,
            )) return
        failureWrites.apply(ticket, PlaybackFailure(
            reason = classifyIosLoadFailure(error),
            occurredAtMillis = Clock.System.now().toEpochMilliseconds(),
        )).onFailure {
            logger.e(it) { "Could not persist iOS playback failure" }
        }
    }

    private fun isSourceReady(audio: LAudio): Boolean = platformMediaSource
        .findEnabledSource(audio.mediaSourceName)
        ?.contentState
        ?.value
        ?.isReady == true

    /**
     * 播放中途失败后继续沿方向找下一首。用独立协程发起：观察器本身会在换歌时被取消，
     * 不能让它把这次导航一起带走。
     */
    private fun navigateAfterStalledFailure(engine: PlaybackEngine, key: MediaKey) {
        launch(Dispatchers.Main) {
            if (engine !== activeEngine || loadedMediaKey != key) return@launch
            if (queue.currentItem()?.mediaKey != key) return@launch
            try {
                skipToNext()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                logger.e(failure) { "Could not continue playback after a stalled failure" }
            }
        }
    }

    private suspend fun playInternal(): Unit = withContext(Dispatchers.Main) {
        volumeFadeHelper.play()
        try {
            val loaded = loadedMediaKey
            val engine = activeEngine
            if (engine != null && loaded != null && loaded == queue.currentItem()?.mediaKey) {
                playRequestedFor = loaded
                engine.play()
                // 观察器可能已因异常退出；恢复播放同样需要一条清除路径。
                if (loadedWatch?.isActive != true) {
                    queue.currentItem()?.let { watchLoadedItem(engine, loaded, failureWrites.register(it.playbackId)) }
                }
            } else {
                val current = queue.currentItem()
                    ?: throw Exception("No media to play")
                logger.i(messageString = "playing: ${current.id} ${current.title} from ${current.mediaSourceName}")
                selectInternal(queue.stateSnapshot().index, true)
            }
        } catch (e: Exception) {
            reportPlaybackCommandFailure(e) {
                Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
                emitError(e)
            }
        }
        Unit
    }

    private suspend fun pauseInternal() {
        playRequestedFor = null
        volumeFadeHelper.pauseAndAwait { activeEngine?.pause() }
    }

    override suspend fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    private suspend fun stopInternal() = withContext(Dispatchers.Main) {
        loadedWatch?.cancel()
        loadedWatch = null
        playRequestedFor = null
        try {
            activeEngine?.stop()
            loadedMediaKey = null
        } catch (e: Exception) {
            reportPlaybackCommandFailure(e) {
                Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
                emitError(e)
            }
        }
        Unit
    }

    private suspend fun selectInternal(index: Int, start: Boolean): Unit = withContext(Dispatchers.Main) {
        val direction = currentCoroutineContext()[PlaybackNavigationContext]?.direction ?: PlaybackDirection.Forward
        val navigationStartedAt = TimeSource.Monotonic.markNow()
        try {
            navigateWithFailureFallback(
                traversal = PlaybackFailureTraversal(),
                initialIndex = index,
                direction = direction,
                playbackMode = { playbackMode.value },
                candidates = { queue.stateSnapshot().list },
                playableSlots = { list -> playableSlots(list) },
                // 只有"用户请求了播放、且这次是真正的加载"才跳；已加载成功后的失败不升级成整队列连跳。
                skipPolicy = { item -> start && !isLoadedItem(item) },
                recordFailure = { item, failure -> persistLoadFailure(item, failureWrites.register(item.playbackId), failure) },
                stop = { stopInternal() },
                attempt = { target, item -> attemptLoad(item, target, start) },
                outOfBudget = { navigationStartedAt.elapsedNow() > DefaultSkipNavigationBudget },
            )
        } catch (e: Exception) {
            reportPlaybackCommandFailure(e) {
                Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
                emitError(e)
            }
        }
    }

    /** 可以尝试的槽位：可用、来源已就绪，且没有已记录的失败。一次跳过搜索只读一次失败表。 */
    private suspend fun playableSlots(list: List<LAudio>): Set<Int> {
        // 失败表读取失败不能顶替原始加载失败：按"没有已知失败"继续尝试，仅记录诊断。
        val known = runCatching { playbackFailureRepository.failures.first() }
            .onFailure { logger.e(it) { "Could not read recorded playback failures; skipping without them" } }
            .getOrDefault(emptyMap())
        return playablePlaybackSlots(list, known.keys, ::isSourceReady)
    }

    /** 已经加载且引擎未处于错误态：选中同一首时走定位 + 播放，不重新加载。 */
    private fun isLoadedItem(item: LAudio): Boolean = activeEngine != null &&
        loadedMediaKey == item.mediaKey &&
        activeEngine?.state?.value?.error == null

    /**
     * 加载（并按需播放）一个槽位。失败向上抛，由导航循环决定跳过还是上报；
     * 成功返回后 [loadedMediaKey] 与队列 current 都指向该槽位。
     */
    private suspend fun attemptLoad(item: LAudio, index: Int, start: Boolean) {
        loadedWatch?.cancel()
        loadedWatch = null
        // 只有这次加载被要求播放时才算"用户想听这首歌"。
        playRequestedFor = item.mediaKey.takeIf { start }
        val successTicket = failureWrites.register(item.playbackId)

        if (isLoadedItem(item)) {
            seekInternal(0)
            queue.update { switchTo(index) }
            prepareSurpriseNext()
            if (start) playInternal() else pauseInternal()
            // 重试同一首时也要重新观察：播放继续推进就清除旧失败，引擎仍报错则保持失败状态。
            activeEngine?.let { engine -> watchLoadedItem(engine, item.mediaKey, successTicket) }
            return
        }

        val mediaData = resolveMediaData(item)
        ensureActive()
        val engine = engineRouter.selectEngine(mediaData, item)
            ?: throw NoEngineFoundException(mediaData, item)

        logger.i { "skipTo[$index] item=${item.title} source=${item.mediaSourceName} engine=${engine::class.simpleName} mediaData=${mediaData::class.simpleName}" }

        if (engine !== activeEngine) {
            logger.i { "switch engine: ${activeEngine?.let { it::class.simpleName }} → ${engine::class.simpleName}" }
            activeEngine?.release()
            activeEngine = engine
        }

        loadedMediaKey = null
        engine.load(mediaData, item)
        ensureActive()
        loadedMediaKey = item.mediaKey
        queue.update { switchTo(index = index) }
        prepareSurpriseNext()
        if (start) engine.play()
        watchLoadedItem(engine, item.mediaKey, successTicket)
    }

    private suspend fun seekInternal(positionMs: Long) = withContext(Dispatchers.Main) {
        try {
            activeEngine?.seekTo(positionMs)
        } catch (e: Exception) {
            reportPlaybackCommandFailure(e) {
                Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
                emitError(e)
            }
        }
        Unit
    }

    override fun currentPosition(): Long {
        return activeEngine?.currentPosition() ?: 0L
    }
}

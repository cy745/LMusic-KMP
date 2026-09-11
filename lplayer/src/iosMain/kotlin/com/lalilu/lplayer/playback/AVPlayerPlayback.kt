package com.lalilu.lplayer.playback

import co.touchlab.kermit.Logger
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.mediaKey
import com.lalilu.lplayer.extensions.VolumeFadeHelper
import com.lalilu.lplayer.helper.AudioSessionHelper
import com.lalilu.lplayer.notifacation.NowPlayingInfoNotification
import com.lalilu.lplayer.notifacation.RemoteCommandHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
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
@Single(binds = [Playback::class])
class AVPlayerPlayback(
    history: PlaybackHistory,
    audioRepository: AudioRepository
) : AbstractPlayback(history = history, audioRepository = audioRepository), KoinComponent {

    companion object {
        const val TAG = "AVPlayerPlayback"
    }

    private val logger = Logger.withTag(TAG)
    private var loadedMediaKey: MediaKey? = null
    private val commands = LatestPlaybackCommand()

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
        }

    private suspend fun playInternal(): Unit = withContext(Dispatchers.Main) {
        volumeFadeHelper.play()
        try {
            if (activeEngine != null && loadedMediaKey != null && loadedMediaKey == queue.currentItem()?.mediaKey) {
                activeEngine?.play()
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
        volumeFadeHelper.pauseAndAwait { activeEngine?.pause() }
    }

    override suspend fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    private suspend fun stopInternal() = withContext(Dispatchers.Main) {
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
        try {
            val state = queue.stateSnapshot()
            val item = state.list.getOrNull(index)
                ?: throw Exception("Invalid index: $index")

            if (loadedMediaKey == item.mediaKey && activeEngine != null) {
                seekInternal(0)
                queue.update { switchTo(index) }
                prepareSurpriseNext()
                if (start) playInternal() else pauseInternal()
                return@withContext
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
        } catch (e: Exception) {
            reportPlaybackCommandFailure(e) {
                Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
                emitError(e)
            }
        }
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

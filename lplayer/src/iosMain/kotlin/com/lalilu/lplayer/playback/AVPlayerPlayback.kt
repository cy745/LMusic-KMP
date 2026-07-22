package com.lalilu.lplayer.playback

import co.touchlab.kermit.Logger
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lplayer.extensions.VolumeFadeHelper
import com.lalilu.lplayer.helper.AudioSessionHelper
import com.lalilu.lplayer.notifacation.NowPlayingInfoNotification
import com.lalilu.lplayer.notifacation.RemoteCommandHandler
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    override fun createEngines(): List<PlaybackEngine> = listOf(
        MusicKitEngine(),
        AVAudioPlayerEngine(),
        AVPlayerEngine(),
    )

    private var volumeFadeHelper = VolumeFadeHelper(
        onSetVolume = { v ->
            val engine = activeEngine
            when {
                engine is AVPlayerEngine -> engine.setVolume(v)
                engine is AVAudioPlayerEngine -> engine.setVolume(v)
            }
        }
    )

    init {
        // 给每个 Engine 绑定 onEvent 回调（覆盖父类的默认绑定，加入日志）
        engineRouter.allEngines.forEach { engine ->
            val original = engine.onEvent
            engine.onEvent = { event ->
                when (event) {
                    is PlaybackEngineEvent.Completion -> {
                        logger.i(messageString = "Engine completion: ${engine::class.simpleName}")
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
            AudioSessionHelper.bindPlayback(this)
        }
    }

    override suspend fun play() = withContext(Dispatchers.Main) {
        volumeFadeHelper.play()
        try {
            AudioSessionHelper.ensureAudioSessionActive()
            if (activeEngine != null) {
                activeEngine?.play()
            } else {
                val current = queue.currentItem()
                    ?: throw Exception("No media to play")
                logger.i(messageString = "playing: ${current.id} ${current.title} from ${current.mediaSourceName}")
                skipTo(queue.stateSnapshot().index, true)
            }
        } catch (e: Exception) {
            Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
            emitError(e)
        }
        Unit
    }

    override suspend fun pause() = withContext(Dispatchers.Main) {
        volumeFadeHelper.pause {
            // activeEngine.pause() is suspend; launch via ApplicationCoroutineScope
        }
        activeEngine?.pause()
        Unit
    }

    override suspend fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    override suspend fun stop() = withContext(Dispatchers.Main) {
        try {
            activeEngine?.stop()
        } catch (e: Exception) {
            Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
            emitError(e)
        }
        Unit
    }

    override suspend fun skipTo(index: Int, start: Boolean) = withContext(Dispatchers.Main) {
        try {
            val state = queue.stateSnapshot()
            if (index == state.index) {
                seekTo(0)
                return@withContext
            }

            val item = state.list.getOrNull(index)
                ?: throw Exception("Invalid index: $index")
            val mediaData = resolveMediaData(item)
            val engine = engineRouter.selectEngine(mediaData, item)
                ?: throw NoEngineFoundException(mediaData, item)

            if (engine !== activeEngine) {
                activeEngine?.release()
                activeEngine = engine
            }

            AudioSessionHelper.ensureAudioSessionActive()
            engine.load(mediaData, item)
            queue.update { switchTo(index = index) }
            if (start) engine.play()
        } catch (e: Exception) {
            Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
            emitError(e)
        }
    }

    override suspend fun seekTo(positionMs: Long) = withContext(Dispatchers.Main) {
        try {
            activeEngine?.seekTo(positionMs)
        } catch (e: Exception) {
            Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
            emitError(e)
        }
        Unit
    }

    override fun currentPosition(): Long {
        return activeEngine?.currentPosition() ?: 0L
    }
}

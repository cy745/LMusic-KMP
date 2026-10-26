package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.source.*
import com.lalilu.lplayer.core.*
import com.lalilu.lplayer.notification.BrowserMediaSessionHelper
import io.github.vinceglb.filekit.utils.toJsArray
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.annotation.Single
import org.w3c.dom.Audio
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

@Single(binds = [Playback::class])
fun provideAudioPlayback(audioRepository: AudioRepository, history: PlaybackHistory,
    platformMediaSource: PlatformMediaSource): CorePlayback {
    val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    PlaybackOutput.report(com.lalilu.playback.core.OutputCapabilities(
        backend = "HTMLAudioElement / 浏览器", original = com.lalilu.playback.core.Support.Unsupported,
        originalReason = "浏览器媒体元素不提供独占输出或 DAC 格式验证接口",
        gapless = com.lalilu.playback.core.Support.Unsupported))
    val engine = BrowserAudioEngine(scope)
    return CorePlayback(scope, EngineAudioRuntime(scope, listOf(engine),
        volume = { _, value -> engine.setVolume(value) }, canFade = { true }),
        platformMediaSource, audioRepository, history).also {
        BrowserMediaSessionHelper.bindPlayback(it)
        it.startHistoryPlayback()
    }
}

/** One element per load: late DOM events can never acquire a newer load's ticket. */
internal class BrowserAudioEngine(private val scope: CoroutineScope) : PlaybackEngine {
    override val state = MutableStateFlow(PlaybackEngineState.EMPTY)
    override var onEvent: (suspend (PlaybackEngineEvent) -> Unit)? = null
    private var player: Audio? = null
    private var objectUrl: String? = null
    private var gain = 0f
    override fun canHandle(mediaData: MediaData, audio: LAudio) = true
    override suspend fun load(mediaData: MediaData, audio: LAudio) {
        release()
        state.value = PlaybackEngineState(isLoading = true)
        val element = Audio()
        player = element
        element.volume = gain.toDouble()
        fun refresh() {
            if (player !== element) return
            val position = (element.currentTime * 1000).toLong().coerceAtLeast(0)
            var buffered = position
            for (i in 0 until element.buffered.length) {
                if (element.currentTime >= element.buffered.start(i) && element.currentTime <= element.buffered.end(i)) {
                    buffered = (element.buffered.end(i) * 1000).toLong()
                    break
                }
            }
            state.value = PlaybackEngineState(isPlaying = !element.paused && !element.ended,
                position = position, duration = if (element.duration.isFinite()) (element.duration * 1000).toLong() else 0,
                bufferedPosition = buffered, isLoading = element.readyState.toInt() < 2,
                error = element.error?.let { "Browser media error: ${it.code}" })
        }
        listOf("canplay", "timeupdate", "durationchange", "progress", "playing", "waiting", "pause", "error")
            .forEach { event -> element.addEventListener(event) { refresh() } }
        element.addEventListener("ended") { if (player === element) scope.launch {
            if (player === element) { refresh(); onEvent?.invoke(PlaybackEngineEvent.Completion) }
        } }
        element.src = when (mediaData) {
            is MediaData.Url -> mediaData.url
            is MediaData.Bytes -> URL.createObjectURL(mediaData.bytes.toJsBlob()).also { objectUrl = it }
        }
        element.load()
    }
    override suspend fun play() {
        val element = player ?: return
        element.play().await()
    }
    override suspend fun pause() { player?.pause() }
    override suspend fun stop() { pause(); player?.currentTime = 0.0 }
    override suspend fun seekTo(positionMs: Long) {
        val element = player ?: return
        element.currentTime = positionMs / 1000.0
        withTimeout(5_000) { while (element.seeking) delay(20) }
    }
    fun setVolume(value: Float) { gain = value; player?.volume = value.toDouble() }
    override fun currentPosition() = player?.let { (it.currentTime * 1000).toLong() } ?: 0L
    override suspend fun release() {
        val old = player
        player = null
        old?.pause(); old?.removeAttribute("src"); old?.load()
        objectUrl?.let(URL::revokeObjectURL)
        objectUrl = null
        state.value = PlaybackEngineState.EMPTY
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
fun ByteArray.toJsBlob(mimeType: String = "application/octet-stream"): Blob =
    Blob(blobParts = toJsArray(), options = BlobPropertyBag(type = mimeType))

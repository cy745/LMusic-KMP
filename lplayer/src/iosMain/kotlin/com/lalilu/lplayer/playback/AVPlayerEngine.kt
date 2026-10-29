package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lplayer.core.IosCachedAsset
import com.lalilu.lplayer.core.NativeContentAccess
import com.lalilu.lplayer.core.preciseAudioAsset
import com.lalilu.lplayer.helper.AudioSessionHelper
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import platform.AVFoundation.*
import platform.CoreMedia.*
import platform.Foundation.*
import platform.darwin.NSObjectProtocol
import kotlin.coroutines.resume
import kotlin.time.Duration

/** Native two-item execution window. Selection and traversal belong exclusively to PlaybackCore. */
@OptIn(ExperimentalForeignApi::class)
class AVPlayerEngine(private val contentAccess: NativeContentAccess? = null) : ContinuousPlaybackEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val player = AVQueuePlayer()
    private val mutableState = MutableStateFlow(PlaybackEngineState.EMPTY)
    override val state = mutableState.asStateFlow()
    override var onEvent: (suspend (PlaybackEngineEvent) -> Unit)? = null
    override var onNext: (suspend () -> Unit)? = null
    private data class Entry(val item: AVPlayerItem, val cached: IosCachedAsset?, val data: MediaData)
    private var current: Entry? = null
    private var next: Entry? = null
    private var observing: Job? = null
    private var endObserver: NSObjectProtocol? = null
    private var autoPlay = false
    private var ended = false

    override fun canHandle(mediaData: MediaData, audio: LAudio) = mediaData is MediaData.Url
    private fun entry(data: MediaData): Entry {
        val uri = (data as MediaData.Url).url
        val cached = contentAccess?.takeIf { it.descriptor(uri) != null }?.let { IosCachedAsset(it, uri) }
        val item = cached?.let { AVPlayerItem(it.asset) }
            ?: AVPlayerItem(preciseAudioAsset(NSURL.URLWithString(uri) ?: error("Invalid media URL")))
        item.preferredForwardBufferDuration = 5.0
        return Entry(item, cached, data)
    }

    override suspend fun load(mediaData: MediaData, audio: LAudio) {
        release()
        val entry = entry(mediaData)
        current = entry
        mutableState.value = PlaybackEngineState(isLoading = true)
        player.insertItem(entry.item, null)
        observeEnd(entry)
        observing = scope.launch {
            while (isActive) {
                val candidate = next
                if (candidate != null && player.currentItem == candidate.item) {
                    val old = current
                    current = candidate; next = null; ended = false
                    old?.cached?.close()
                    observeEnd(candidate)
                    publish(candidate)
                    onNext?.invoke()
                } else if (candidate?.item?.status == AVPlayerItemStatusFailed) {
                    cancelNext() // Speculative failure never skips the current song.
                }
                current?.let(::publish)
                delay(20)
            }
        }
    }

    private fun observeEnd(entry: Entry) {
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            AVPlayerItemDidPlayToEndTimeNotification, entry.item, NSOperationQueue.mainQueue) {
            scope.launch {
                if (current === entry && next == null && !ended) {
                    ended = true; autoPlay = false
                    mutableState.update { it.copy(isPlaying = false) }
                    onEvent?.invoke(PlaybackEngineEvent.Completion)
                }
            }
        }
    }

    private fun publish(entry: Entry) {
        val item = entry.item
        val ready = item.status == AVPlayerItemStatusReadyToPlay
        val failed = item.status == AVPlayerItemStatusFailed
        if (ready && autoPlay) { autoPlay = false; player.play() }
        val position = currentPosition()
        val duration = item.duration.useContents { toMilliseconds().toLong().coerceAtLeast(0) }
        val buffered = bufferedPositionAt(position)
        mutableState.value = PlaybackEngineState(
            isPlaying = !ended && player.rate > 0f,
            position = position,
            duration = duration,
            bufferedPosition = buffered,
            isLoading = !ready && !failed,
            error = if (failed) "AVFoundation failed to prepare media" else null,
        )
    }

    override fun bufferedPositionAt(positionMs: Long): Long {
        val entry = current ?: return positionMs
        val item = entry.item
        var buffered = positionMs
        item.loadedTimeRanges.forEach { value ->
            val range = (value as NSValue).CMTimeRangeValue
            val start = range.useContents { start.toMilliseconds() }
            val end = CMTimeGetSeconds(CMTimeRangeGetEnd(range)) * 1000
            if (start <= positionMs + 1 && end >= positionMs) buffered = maxOf(buffered, end.toLong())
        }
        if (entry.cached != null && contentAccess?.canPreloadAfter(entry.data) == true) {
            buffered = maxOf(buffered, item.duration.useContents { toMilliseconds().toLong() })
        }
        return buffered
    }

    override suspend fun stageNext(mediaData: MediaData, audio: LAudio) {
        cancelNext()
        val candidate = entry(mediaData)
        if (!player.canInsertItem(candidate.item, current?.item)) { candidate.cached?.close(); return }
        next = candidate
        player.insertItem(candidate.item, current?.item)
    }
    override fun cancelNext(): Boolean {
        val crossed = next?.item?.let { it == player.currentItem } == true
        if (crossed) player.pause()
        next?.let { player.removeItem(it.item); it.cached?.close() }
        next = null
        return crossed
    }
    override suspend fun awaitPreparation(timeout: Duration) {
        val entry = current ?: return
        try {
            val state = withTimeout(timeout) { state.first { !it.isLoading } }
            if (current !== entry) throw CancellationException("Media selection replaced")
            check(state.error == null) { state.error ?: "Media preparation failed" }
        } catch (cancelled: CancellationException) {
            if (current === entry) release()
            throw cancelled
        }
    }
    override suspend fun play() {
        AudioSessionHelper.ensureAudioSessionActive()
        if (current?.item?.status == AVPlayerItemStatusReadyToPlay) {
            player.play(); mutableState.update { it.copy(isPlaying = true) }
        } else autoPlay = true
    }
    override suspend fun pause() { autoPlay = false; player.pause(); mutableState.update { it.copy(isPlaying = false) } }
    override suspend fun stop() { pause(); seekTo(0) }
    override suspend fun seekTo(positionMs: Long) {
        val entry = current ?: return
        suspendCancellableCoroutine<Unit> { continuation ->
            // Lyrics require the decoded audio to land on the requested time, not a nearby packet.
            player.seekToTime(CMTimeMake(positionMs, 1000),
                toleranceBefore = CMTimeMake(0, 1), toleranceAfter = CMTimeMake(0, 1), completionHandler = { finished ->
                if (continuation.isActive) {
                    if (finished && current === entry) continuation.resume(Unit)
                    else continuation.cancel(CancellationException("Native seek replaced"))
                }
            })
        }
        ended = false
        publish(entry)
    }
    fun setVolume(volume: Float) { player.volume = volume }
    override fun currentPosition(): Long = player.currentTime().useContents { toMilliseconds().toLong().coerceAtLeast(0) }
    override suspend fun release() {
        autoPlay = false; ended = false
        observing?.cancel(); observing = null
        player.pause()
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }; endObserver = null
        cancelNext()
        player.removeAllItems()
        current?.cached?.close(); current = null
        mutableState.value = PlaybackEngineState.EMPTY
    }
}

fun CMTime.toMilliseconds(): Double = if (timescale == 0) 0.0 else value.toDouble() / timescale * 1000.0

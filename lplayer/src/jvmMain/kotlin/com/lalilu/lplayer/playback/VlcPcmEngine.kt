package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lplayer.core.NativeContentAccess
import com.lalilu.lplayer.player.ByteArrayCallbackMedia
import com.lalilu.lplayer.player.CachedCallbackMedia
import com.sun.jna.Pointer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import uk.co.caprica.vlcj.player.base.*
import uk.co.caprica.vlcj.player.base.callback.AudioCallbackAdapter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Two bounded decoders feeding one persistent PCM output; never owns queue traversal policy. */
internal class VlcPcmEngine(
    private val scope: CoroutineScope,
    private val provider: suspend () -> MediaPlayer,
    private val access: NativeContentAccess? = null,
    private val sinkFactory: () -> PcmSink = ::JavaSoundPcmSink,
    private val fallbackFactory: (() -> VlcEngine)? = null,
) : ContinuousPlaybackEngine {
    override val state = MutableStateFlow(PlaybackEngineState.EMPTY)
    override var onEvent: (suspend (PlaybackEngineEvent) -> Unit)? = null
    override var onNext: (suspend () -> Unit)? = null
    private val lock = ReentrantLock(true) // Output writes must not starve state/command delivery.
    private var current: Entry? = null
    private var candidate: Entry? = null
    private var writer: Entry? = null
    private var sink: PcmSink? = null
    private var writtenFrames = 0L
    private var observation: Job? = null
    private var pump: Thread? = null
    @Volatile private var running = false
    @Volatile private var playing = false
    @Volatile private var seeking = false
    @Volatile private var gain = 0f
    @Volatile private var outputFailure: String? = null
    private var completionSent = false
    private var fallback: VlcEngine? = null
    override val continuousOutputAvailable: Boolean get() = fallback == null && sink != null
    val player: MediaPlayer? get() = fallback?.player ?: current?.native

    private class Entry(val native: MediaPlayer, val data: MediaData, val cached: CachedCallbackMedia?) {
        val pcm = ArrayBlockingQueue<Pair<Long, ByteArray>>(500) // <=5 seconds, each packet is <=10 ms.
        val decodedFrames = AtomicLong()
        val generation = AtomicLong()
        private val closed = AtomicBoolean()
        @Volatile var dead = false
        @Volatile var ended = false
        @Volatile var drained = false
        @Volatile var error: String? = null
        @Volatile var ready = false
        @Volatile var baseMs = 0L
        @Volatile var startFrame = 0L
        fun clear() { generation.incrementAndGet(); pcm.clear(); decodedFrames.set(0); ended = false; drained = false }
        fun cancel() { dead = true; cached?.cancel(); pcm.clear() }
        fun close() { if (closed.compareAndSet(false, true)) { cancel(); native.controls().stop(); native.release(); cached?.close() } }
    }

    override fun canHandle(mediaData: MediaData, audio: LAudio) = true
    private suspend fun decoder(data: MediaData, startMs: Long = 0): Entry {
        val native = provider()
        val cached = (data as? MediaData.Url)?.url?.takeIf { access?.descriptor(it) != null }
            ?.let { CachedCallbackMedia(access!!, it) }
        val entry = Entry(native, data, cached)
        native.audio().callback("S16N", PCM_RATE, 2, object : AudioCallbackAdapter() {
            override fun play(mediaPlayer: MediaPlayer, samples: Pointer, sampleCount: Int, pts: Long) {
                val generation = entry.generation.get()
                var offset = 0
                val length = sampleCount * PCM_FRAME_BYTES
                while (offset < length && !entry.dead && generation == entry.generation.get()) {
                    val count = minOf(PCM_RATE / 100 * PCM_FRAME_BYTES, length - offset)
                    val bytes = samples.getByteArray(offset.toLong(), count)
                    while (!entry.dead && generation == entry.generation.get() && !entry.pcm.offer(generation to bytes, 20, TimeUnit.MILLISECONDS)) { /* bounded backpressure */ }
                    if (!entry.dead && generation == entry.generation.get()) { entry.decodedFrames.addAndGet(count.toLong() / PCM_FRAME_BYTES); entry.ready = true }
                    offset += count
                }
            }
            override fun flush(mediaPlayer: MediaPlayer, pts: Long) { entry.clear() }
            override fun drain(mediaPlayer: MediaPlayer) {
                entry.ended = true
                while (!entry.dead && !entry.drained) Thread.sleep(2)
            }
        }, true)
        native.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun error(mediaPlayer: MediaPlayer) { entry.error = "VLC audio decoding failed" }
            override fun finished(mediaPlayer: MediaPlayer) { entry.ended = true }
        })
        try {
            val options = if (startMs > 0) arrayOf(":start-time=${startMs / 1000.0}") else emptyArray()
            val accepted = when {
                cached != null -> native.media().prepare(cached, *options)
                data is MediaData.Url -> native.media().prepare(data.url, *options)
                data is MediaData.Bytes -> native.media().prepare(ByteArrayCallbackMedia.obtain(data.bytes), *options)
                else -> false
            }
            check(accepted) { "VLC rejected audio" }
            native.controls().play()
            currentCoroutineContext().ensureActive()
            return entry
        } catch (error: Exception) { withContext(NonCancellable + Dispatchers.IO) { entry.close() }; throw error }
    }

    override suspend fun load(mediaData: MediaData, audio: LAudio) {
        release()
        state.value = PlaybackEngineState(isLoading = true)
        val output = try { sinkFactory() } catch (error: Exception) {
            if (error !is javax.sound.sampled.LineUnavailableException && error !is IllegalArgumentException) throw error
            val native = fallbackFactory?.invoke() ?: throw error
            fallback = native
            native.onEvent = { onEvent?.invoke(it) }
            native.setVolume(gain)
            native.load(mediaData, audio)
            observation = scope.launch { native.state.collect { state.value = it } }
            com.lalilu.lplayer.core.PlaybackOutput.report(com.lalilu.playback.core.OutputCapabilities(
                backend = "VLC / 系统音频回退", original = com.lalilu.playback.core.Support.Unsupported,
                originalReason = "共享 PCM 输出不可用，已回退到 VLC 系统输出；未验证原样输出",
                gapless = com.lalilu.playback.core.Support.Unsupported))
            return
        }
        sink = output
        com.lalilu.lplayer.core.PlaybackOutput.report(com.lalilu.playback.core.OutputCapabilities(
            backend = "VLC / 共享 PCM 输出", original = com.lalilu.playback.core.Support.Unsupported,
            originalReason = "当前使用体验模式 PCM 输出，未接入独占或系统重采样旁路",
            gapless = com.lalilu.playback.core.Support.Unknown,
            apiFormat = com.lalilu.playback.core.OutputFormat(PCM_RATE, 2, "PCM 16-bit"),
            apiFormatEvidence = "应用输出通道配置；不是物理 DAC 格式证明"))
        try {
            val entry = decoder(mediaData)
            lock.withLock { current = entry; writer = entry; writtenFrames = output.playedFrames; entry.startFrame = writtenFrames }
            running = true
            pump = Thread({ pumpOutput() }, "LMusic-PCM-output").apply { isDaemon = true; start() }
            observation = scope.launch {
                while (isActive) {
                    var retired: Entry? = null
                    var advanced = false
                    var completed = false
                    lock.withLock {
                        val next = candidate
                        if (next != null && writer === next && output.playedFrames >= next.startFrame) {
                            retired = current; retired?.drained = true
                            current = next; candidate = null; advanced = true
                        }
                        val selected = current
                        if (selected != null) {
                            val duration = selected.native.status().length().coerceAtLeast(0)
                            val position = position(selected)
                            val failure = outputFailure ?: selected.error
                            // EOF before even one decoded sample is an invalid/non-audio input.
                            val empty = selected.ended && !selected.ready
                            state.value = PlaybackEngineState(isPlaying = playing && selected.ready && failure == null,
                                position = position, duration = duration, bufferedPosition = bufferedPositionAt(position),
                                isLoading = !selected.ready && failure == null && !empty,
                                error = failure ?: if (empty) "VLC produced no audio" else null)
                            if (playing && writer === selected && selected.ended && selected.pcm.isEmpty() && candidate == null && output.playedFrames >= writtenFrames && !completionSent) {
                                selected.drained = true
                                completionSent = true; playing = false; state.value = state.value.copy(isPlaying = false); completed = true
                            }
                        }
                    }
                    if (advanced) onNext?.invoke()
                    retired?.let { withContext(Dispatchers.IO) { it.close() } }
                    if (completed) onEvent?.invoke(PlaybackEngineEvent.Completion)
                    delay(10)
                }
            }
        } catch (error: Exception) { output.close(); sink = null; throw error }
    }

    private fun pumpOutput() {
        try {
            while (running) {
                var wrote = false
                lock.withLock {
                    val entry = writer
                    val output = sink
                    if (playing && !seeking && entry != null && output != null) {
                        var bytes = pollCurrent(entry)
                        if (bytes == null && entry.ended) {
                            val next = candidate
                            if (next != null && next !== entry && next.ready && next.error == null) {
                                next.startFrame = writtenFrames
                                writer = next
                                bytes = pollCurrent(next)
                            }
                        }
                        if (bytes != null) {
                            output.write(scale(bytes, gain))
                            writtenFrames += bytes.size / PCM_FRAME_BYTES
                            wrote = true
                        }
                    }
                }
                if (!wrote) Thread.sleep(2)
            }
        } catch (error: Exception) { if (running) outputFailure = error.message ?: "Audio output failed" }
    }

    private fun pollCurrent(entry: Entry): ByteArray? {
        while (true) {
            val packet = entry.pcm.poll() ?: return null
            if (packet.first == entry.generation.get()) return packet.second
        }
    }

    private fun scale(bytes: ByteArray, volume: Float): ByteArray {
        if (volume >= 1f) return bytes
        for (i in bytes.indices step 2) {
            val sample = ((bytes[i].toInt() and 255) or (bytes[i + 1].toInt() shl 8)).toShort().toInt()
            val scaled = (sample * volume).toInt().coerceIn(-32768, 32767)
            bytes[i] = scaled.toByte(); bytes[i + 1] = (scaled shr 8).toByte()
        }
        return bytes
    }
    private fun position(entry: Entry): Long = entry.baseMs + ((sink?.playedFrames ?: entry.startFrame) - entry.startFrame).coerceAtLeast(0) * 1000 / PCM_RATE
    override fun currentPosition(): Long = fallback?.currentPosition() ?: lock.withLock { current?.let(::position) ?: 0 }
    override fun bufferedPositionAt(positionMs: Long): Long = lock.withLock {
        fallback?.let { return@withLock it.bufferedPositionAt(positionMs) }
        val entry = current ?: return@withLock positionMs
        if (entry.cached != null && access?.canPreloadAfter(entry.data) == true) maxOf(positionMs, entry.native.status().length())
        else maxOf(positionMs, entry.baseMs + entry.decodedFrames.get() * 1000 / PCM_RATE)
    }
    fun setVolume(value: Float) { gain = value.coerceIn(0f, 1f); fallback?.setVolume(gain) }
    override suspend fun play() { fallback?.let { it.play(); return }; lock.withLock { playing = true; sink?.start() } }
    override suspend fun pause() { fallback?.let { it.pause(); return }; lock.withLock { playing = false; sink?.pause(); state.value = state.value.copy(isPlaying = false) } }
    override suspend fun stop() { pause(); seekTo(0) }
    override suspend fun stageNext(mediaData: MediaData, audio: LAudio) {
        if (fallback != null) return
        cancelNext()
        val entry = decoder(mediaData)
        lock.withLock { candidate = entry }
    }
    override fun cancelNext(): Boolean {
        val removed: Entry?
        val crossed: Boolean
        lock.withLock {
            removed = candidate
            crossed = removed != null && writer === removed
            if (crossed) { sink?.flush(); writer = current; writtenFrames = sink?.playedFrames ?: 0 }
            candidate = null
            removed?.cancel()
        }
        removed?.let { scope.launch(Dispatchers.IO) { it.close() } }
        return crossed
    }
    override suspend fun seekTo(positionMs: Long) {
        fallback?.let { it.seekTo(positionMs); return }
        cancelNext()
        val old: Entry
        lock.withLock {
            old = current ?: return
            seeking = true
            sink?.pause(); sink?.flush(); old.cancel()
            current = null; writer = null
            state.value = state.value.copy(isLoading = true, position = positionMs)
            completionSent = false
        }
        try {
            // Reopen only the decoder; keeping the output avoids native drain/seek deadlocks when
            // a short track has already decoded completely into the bounded PCM queue.
            withContext(NonCancellable + Dispatchers.IO) { old.close() }
            val entry = decoder(old.data, positionMs)
            lock.withLock {
                writtenFrames = sink?.playedFrames ?: 0
                entry.startFrame = writtenFrames; entry.baseMs = positionMs
                current = entry; writer = entry
            }
            withTimeout(30_000) {
                while (!entry.ready) { check(entry.error == null && !entry.ended) { entry.error ?: "Seek produced no audio" }; delay(10) }
            }
        } finally { lock.withLock { seeking = false; if (playing) sink?.start() } }
    }
    override suspend fun release() {
        observation?.cancel(); observation = null
        fallback?.release(); fallback = null
        running = false; playing = false; seeking = false
        // Closing the device first also wakes a write blocked by a disappearing output route.
        val output = sink
        output?.close()
        val entries = lock.withLock {
            listOfNotNull(current, candidate).distinct().also {
                it.forEach(Entry::cancel)
                current = null; candidate = null; writer = null; sink = null
            }
        }
        withContext(NonCancellable + Dispatchers.IO) {
            pump?.join(2000); pump = null
            entries.forEach(Entry::close)
        }
        outputFailure = null; completionSent = false
        state.value = PlaybackEngineState.EMPTY
    }
}

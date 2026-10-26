package com.lalilu.lplayer.core

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lplayer.playback.*
import com.lalilu.playback.core.*
import kotlinx.coroutines.*

/** Adapts a single native decoder, never a second queue/policy owner. All calls use [scope]'s dispatcher. */
class EngineAudioRuntime(
    private val scope: CoroutineScope,
    private val engines: List<PlaybackEngine>,
    private val volume: (PlaybackEngine, Float) -> Unit = { _, _ -> },
    private val canFade: (PlaybackEngine) -> Boolean = { false },
) : AudioRuntime {
    var onObservation: ((RuntimeEvent) -> Unit)? = null
    private fun emit(event: RuntimeEvent) { onObservation?.invoke(event); onEvent?.invoke(event) }
    override var onEvent: ((RuntimeEvent) -> Unit)? = null
    var activeEngine: PlaybackEngine? = null
        private set
    private var owner: LoadTicket? = null
    private var preparing: Job? = null
    private var observing: Job? = null
    private var fading: Job? = null
    private var teardown: Job? = null
    private var seeking: Job? = null
    private var desiredPlaying = false
    private var gain = 1f
    private var serial = 0L
    private val resources = mutableMapOf<String, Pair<MediaData, LAudio>>()

    /** Opaque, one-use handoff: the core never sees credentials, byte arrays or native objects. */
    fun resource(data: MediaData, audio: LAudio): PlayableResource {
        val id = "engine-resource:${++serial}"
        resources.clear() // This non-preloading adapter has exactly one resolving current item.
        resources[id] = data to audio
        return PlayableResource(id)
    }

    override fun silence(next: LoadTicket?) {
        owner = next
        desiredPlaying = false
        preparing?.cancel(); observing?.cancel(); fading?.cancel(); seeking?.cancel()
        val engine = activeEngine
        engine?.onEvent = null
        val previous = teardown
        teardown = scope.launch {
            previous?.join()
            if (engine != null && engine === activeEngine) {
                if (engine.state.value.isPlaying) fade(engine, 0f)
                engine.release()
                if (activeEngine === engine) activeEngine = null
            }
        }
    }

    override fun prepare(ticket: LoadTicket, resource: PlayableResource) {
        if (ticket != owner) return
        val payload = resources.remove(resource.uri) ?: return fail(ticket, "Prepared content expired")
        preparing = scope.launch {
            try {
                teardown?.join()
                ensureActive()
                if (owner != ticket) return@launch
                val (data, audio) = payload
                val engine = engines.firstOrNull { it.canHandle(data, audio) }
                    ?: error("No decoder supports this content")
                activeEngine = engine
                gain = 0f
                volume(engine, gain)
                engine.onEvent = { event ->
                    if (owner == ticket && activeEngine === engine) when (event) {
                        PlaybackEngineEvent.Completion -> { desiredPlaying = false; engine.pause(); emit(RuntimeEvent.Ended(ticket)) }
                        is PlaybackEngineEvent.Error -> fail(ticket, event.throwable.message ?: "Decoder error")
                    }
                }
                engine.load(data, audio)
                volume(engine, gain)
                engine.awaitPreparation()
                ensureActive()
                if (owner != ticket) return@launch
                emit(RuntimeEvent.Prepared(ticket, engine.state.value.duration))
                observing = scope.launch {
                    var started = false
                    while (isActive && owner == ticket) {
                        val state = engine.state.value
                        if (state.error != null) { fail(ticket, state.error); break }
                        if (state.isLoading) emit(RuntimeEvent.Buffering(ticket))
                        if (started && !state.isPlaying && !state.isLoading && desiredPlaying) {
                            desiredPlaying = false
                            emit(RuntimeEvent.PausedBySystem(ticket))
                        }
                        if (state.isPlaying && !state.isLoading && !started) emit(RuntimeEvent.Started(ticket))
                        started = state.isPlaying && !state.isLoading
                        emit(RuntimeEvent.Position(ticket, engine.currentPosition().coerceAtLeast(0),
                            state.duration.coerceAtLeast(0), state.bufferedPosition.coerceAtLeast(0)))
                        delay(100)
                    }
                }
            } catch (timeout: TimeoutCancellationException) { fail(ticket, "Native operation timed out") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { fail(ticket, failure.message ?: "Preparation failed") }
        }
    }

    override fun setPlaying(ticket: LoadTicket, playing: Boolean, smooth: Boolean) {
        if (owner != ticket) return
        desiredPlaying = playing
        fading?.cancel()
        fading = scope.launch {
            try {
                val engine = activeEngine ?: return@launch
                if (playing) {
                    engine.play()
                    if (smooth) fade(engine, 1f) else { gain = 1f; volume(engine, gain) }
                } else {
                    if (smooth) fade(engine, 0f)
                    engine.pause()
                    gain = 0f; volume(engine, gain)
                }
            } catch (timeout: TimeoutCancellationException) { fail(ticket, "Native operation timed out") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { fail(ticket, failure.message ?: "Playback control failed") }
        }
    }

    override fun seek(ticket: LoadTicket, seekId: Long, positionMs: Long) {
        if (owner != ticket) return
        seeking?.cancel()
        seeking = scope.launch {
            try {
                val engine = activeEngine ?: return@launch
                engine.seekTo(positionMs)
                if (owner == ticket) emit(RuntimeEvent.SeekSettled(ticket, seekId, engine.currentPosition()))
            } catch (timeout: TimeoutCancellationException) { fail(ticket, "Native operation timed out") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { fail(ticket, failure.message ?: "Seek failed") }
        }
    }

    private suspend fun fade(engine: PlaybackEngine, target: Float) {
        val from = gain
        if (canFade(engine)) repeat(6) { step ->
            gain = from + (target - from) * (step + 1) / 6
            volume(engine, gain)
            delay(10)
        }
        gain = target
        volume(engine, target)
    }

    private fun fail(ticket: LoadTicket, message: String) {
        if (owner == ticket) emit(RuntimeEvent.Failed(ticket, message))
    }

    override fun release() { silence(null); resources.clear() }
}

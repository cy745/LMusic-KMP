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
    val contentAccess: NativeContentAccess? = null,
) : PreloadingAudioRuntime {
    var onObservation: ((RuntimeEvent) -> Unit)? = null
    private fun emit(event: RuntimeEvent) { onObservation?.invoke(event); onEvent?.invoke(event) }
    override var onEvent: ((RuntimeEvent) -> Unit)? = null
    override val supportsPreloading: Boolean get() = (activeEngine as? ContinuousPlaybackEngine)?.continuousOutputAvailable == true
    var activeEngine: PlaybackEngine? = null
        private set
    private var owner: LoadTicket? = null
    private var preparing: Job? = null
    private var observing: Job? = null
    private var fading: Job? = null
    private var teardown: Job? = null
    private var seeking: Job? = null
    private var desiredPlaying = false
    private var playRevision = 0L
    private var gain = 1f
    private var staged: NextPlan? = null
    private var awaitingConfirmation: NextPlan? = null
    private var staging: Job? = null
    private var currentData: MediaData? = null
    private var serial = 0L
    private val resources = mutableMapOf<String, Pair<MediaData, LAudio>>()

    /** Opaque, one-use handoff: the core never sees credentials, byte arrays or native objects. */
    fun resource(data: MediaData, audio: LAudio): PlayableResource {
        val id = "engine-resource:${++serial}"
        // Current and the core-authorized successor may resolve independently.
        resources[id] = data to audio
        return PlayableResource(id)
    }

    override fun silence(next: LoadTicket?) {
        cancelNext()
        resources.clear()
        contentAccess?.selectCurrent(null)
        owner = next
        desiredPlaying = false
        preparing?.cancel(); observing?.cancel(); fading?.cancel(); seeking?.cancel()
        val engine = activeEngine
        engine?.onEvent = null
        val previous = teardown
        teardown = scope.launch {
            previous?.join()
            if (engine != null && engine === activeEngine) {
                if (engine.state.value.isPlaying) fade(engine, 0f, VolumeTransition.TrackChange)
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
                currentData = data
                contentAccess?.selectCurrent(data)
                val engine = engines.firstOrNull { it.canHandle(data, audio) }
                    ?: error("No decoder supports this content")
                activeEngine = engine
                gain = 0f
                volume(engine, gain)
                bindEvents(engine, ticket)
                engine.load(data, audio)
                volume(engine, gain)
                engine.awaitPreparation()
                ensureActive()
                if (owner != ticket) return@launch
                emit(RuntimeEvent.Prepared(ticket, engine.state.value.duration))
                observe(engine, ticket)
            } catch (timeout: TimeoutCancellationException) { fail(ticket, "Native operation timed out") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { fail(ticket, failure.message ?: "Preparation failed") }
        }
    }

    private fun bindEvents(engine: PlaybackEngine, ticket: LoadTicket) {
        engine.onEvent = { event ->
            if (owner == ticket && activeEngine === engine) when (event) {
                PlaybackEngineEvent.Completion -> { desiredPlaying = false; engine.pause(); emit(RuntimeEvent.Ended(ticket)) }
                is PlaybackEngineEvent.Error -> fail(ticket, event.throwable.message ?: "Decoder error")
            }
        }
    }

    private fun observe(engine: PlaybackEngine, ticket: LoadTicket) {
        observing?.cancel()
        observing = scope.launch {
            var started = false
            var observedPlayRevision = -1L
            while (isActive && owner == ticket) {
                val state = engine.state.value
                if (state.error != null) { fail(ticket, state.error); break }
                if (state.isLoading) emit(RuntimeEvent.Buffering(ticket))
                if (started && observedPlayRevision == playRevision && !state.isPlaying && !state.isLoading && desiredPlaying) {
                    desiredPlaying = false
                    emit(RuntimeEvent.PausedBySystem(ticket))
                }
                if (state.isPlaying && !state.isLoading && desiredPlaying && (!started || observedPlayRevision != playRevision)) {
                    observedPlayRevision = playRevision
                    emit(RuntimeEvent.Started(ticket))
                }
                started = state.isPlaying && !state.isLoading
                emit(RuntimeEvent.Position(ticket, engine.currentPosition().coerceAtLeast(0),
                    state.duration.coerceAtLeast(0), state.bufferedPosition.coerceAtLeast(0)))
                delay(100)
            }
        }
    }

    override fun stageNext(plan: NextPlan, resource: PlayableResource) {
        val payload = resources.remove(resource.uri) ?: return
        if (owner != plan.from) return
        cancelNext()
        val engine = activeEngine as? ContinuousPlaybackEngine ?: return
        if (!engine.canHandle(payload.first, payload.second)) return
        staged = plan
        staging = scope.launch {
            try {
                while (currentData?.let { contentAccess?.canPreloadAfter(it) } == false) delay(100)
                ensureActive()
                if (owner != plan.from || staged != plan) return@launch
                engine.onNext = {
                    if (staged == plan && owner == plan.from) {
                        staged = null
                        awaitingConfirmation = plan
                        owner = plan.next
                        bindEvents(engine, plan.next)
                        currentData = payload.first
                        contentAccess?.selectCurrent(payload.first)
                        if (!desiredPlaying) engine.pause()
                        emit(RuntimeEvent.Transitioned(plan, engine.state.value.duration, engine.currentPosition(), engine.state.value.isPlaying))
                        observe(engine, plan.next)
                    }
                }
                engine.stageNext(payload.first, payload.second)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (staged == plan) cancelNext() }
        }
    }

    override fun confirmNext(plan: NextPlan) {
        if (awaitingConfirmation == plan) awaitingConfirmation = null
    }

    override fun cancelNext() {
        staging?.cancel(); staging = null
        staged = null
        val engine = activeEngine as? ContinuousPlaybackEngine
        val crossed = engine?.let { it.onNext = null; it.cancelNext() } == true
        if (crossed && awaitingConfirmation == null) owner?.let { emit(RuntimeEvent.Ended(it)) }
        awaitingConfirmation?.let { plan ->
            awaitingConfirmation = null
            volume(activeEngine ?: return@let, 0f)
            scope.launch(start = CoroutineStart.UNDISPATCHED) { engine?.pause() }
            emit(RuntimeEvent.Ended(plan.from))
        }
    }

    override fun setPlaying(ticket: LoadTicket, playing: Boolean, transition: VolumeTransition) {
        if (owner != ticket) return
        val unchangedIntent = desiredPlaying == playing
        if (playing && !unchangedIntent) playRevision++
        desiredPlaying = playing
        if (unchangedIntent && fading?.isActive == true && transition != VolumeTransition.Immediate) return
        fading?.cancel()
        fading = scope.launch {
            try {
                val engine = activeEngine ?: return@launch
                if (playing) {
                    engine.play()
                    if (transition != VolumeTransition.Immediate) fade(engine, 1f, transition) else { gain = 1f; volume(engine, gain) }
                } else {
                    if (transition != VolumeTransition.Immediate && engine.state.value.isPlaying) fade(engine, 0f, transition)
                    engine.pause()
                    gain = 0f; volume(engine, gain)
                }
            } catch (timeout: TimeoutCancellationException) { fail(ticket, "Native operation timed out") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { fail(ticket, failure.message ?: "Playback control failed") }
        }
    }

    override fun bufferedPositionAt(ticket: LoadTicket, positionMs: Long): Long =
        if (owner == ticket) activeEngine?.bufferedPositionAt(positionMs) ?: positionMs else positionMs

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

    private suspend fun fade(engine: PlaybackEngine, target: Float, transition: VolumeTransition) {
        rampVolume(gain, target, if (canFade(engine)) transition else VolumeTransition.Immediate) {
            gain = it
            volume(engine, it)
        }
    }

    private fun fail(ticket: LoadTicket, message: String) {
        if (owner == ticket) emit(RuntimeEvent.Failed(ticket, message))
    }

    override fun release() { silence(null); resources.clear(); contentAccess?.close() }
}

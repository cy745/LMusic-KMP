package com.lalilu.lplayer.core

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.PlaybackFailure
import com.lalilu.lmedia.domain.model.PlaybackFailureReason
import com.lalilu.lmedia.domain.repository.PlaybackFailureRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.source.resolveMediaData
import com.lalilu.lplayer.playback.*
import com.lalilu.playback.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.mp.KoinPlatform

/** Shared production facade for non-Android hosts. Native runtimes execute, the core decides. */
@OptIn(ExperimentalTime::class)
open class CorePlayback(
    private val scope: CoroutineScope,
    private val runtime: EngineAudioRuntime,
    private val sources: PlatformMediaSource,
    private val repository: AudioRepository,
    private val history: PlaybackHistory,
    private val tracker: IPlaybackDataTracker? = null,
    failureRepository: PlaybackFailureRepository? = null,
    private val classifyFailure: (Throwable) -> PlaybackFailureReason = { PlaybackFailureReason.Unknown },
) : Playback, PlaybackHistory by history, HistoryPositionProvider {
    private val gate = Mutex()
    private val items = mutableMapOf<TrackKey, LAudio>()
    private val failureWrites = failureRepository?.let(::PlaybackFailureWrites)
    private var observedStart: LoadTicket? = null
    private var observedPosition = 0L
    val core = PlaybackCore(scope, ContentResolver { key ->
        val audio = items[key] ?: error("Selected song metadata is unavailable")
        val data = try { sources.resolveMediaData(audio) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { recordFailure(audio, classifyFailure(error)); throw error }
        currentCoroutineContext().ensureActive()
        runtime.resource(data, audio)
    }, runtime, initialMode = history.historyStorage.savedPlaybackMode().toCore())
    private val queueState = MutableStateFlow(QueueState())
    private val playing = MutableStateFlow(false)
    private val duration = MutableStateFlow(0L)
    private val buffered = MutableStateFlow(0L)
    private val engineKey = MutableStateFlow<String?>(null)
    private val mode = MutableStateFlow(history.historyStorage.savedPlaybackMode())
    private val next = MutableStateFlow<LAudio?>(null)
    private val failures = MutableSharedFlow<Throwable>(extraBufferCapacity = 8)
    private var historyRestorer: HistoryQueueRestorer? = null
    private var historyStarted = false
    private var lastError: Pair<Long, String>? = null
    private var lastEpoch = -1L
    private var lastPlaying = false
    private var controlRevision = 0L
    data class InterruptionToken(val revision: Long, val ticket: LoadTicket)
    override val isPlaying = playing.asStateFlow()
    override val errors = failures.asSharedFlow()
    override val currentDuration = duration.asStateFlow()
    override val currentEngineItemKey = engineKey.asStateFlow()
    override val currentBufferedPosition = buffered.asStateFlow()
    override val playbackMode = mode.asStateFlow()
    override val pendingNext = next.asStateFlow()
    override val queue: PlayableQueue = object : PlayableQueue {
        override val expandedItems = queueState.asStateFlow()
        override suspend fun update(updateReason: QueueUpdateReason, predicate: (QueueState) -> Boolean,
            block: QueueUpdateRequest.() -> Unit) = withContext(scope.coroutineContext.minusKey(Job)) {
            gate.withLock {
                publish()
                val old = queueState.value
                if (!predicate(old)) return@withLock
                val mutation = QueueUpdateRequest(old).apply(block)
                applyQueue(mutation.build(updateReason), mutation.replacesAll && updateReason != QueueUpdateReason.Sync)
            }
        }
        override fun nextOf(target: LAudio): LAudio? = queueState.value.list.let { list ->
            list.getOrNull(list.indexOfFirst { it.key() == target.key() }.takeIf { it >= 0 }?.plus(1) ?: -1)
        }
        override fun previousOf(target: LAudio): LAudio? = queueState.value.list.let { list ->
            list.getOrNull(list.indexOfFirst { it.key() == target.key() } - 1)
        }
    }
    private val durationResolver = PlaybackDurationResolver(queue.currentItemFlow(), duration, scope,
        engineItemKey = engineKey)
    override val stableDuration = durationResolver.stableDuration

    init {
        runtime.onObservation = { event ->
            when (event) {
                is RuntimeEvent.Failed -> items[event.ticket.key]?.let { recordFailure(it, PlaybackFailureReason.Decode) }
                is RuntimeEvent.Started -> { observedStart = event.ticket; observedPosition = core.state.value.positionMs }
                is RuntimeEvent.Position -> if (observedStart == event.ticket && event.positionMs > observedPosition) {
                    observedStart = null
                    val audio = items[event.ticket.key]
                    val writes = failureWrites
                    if (audio != null && writes != null) {
                        val receipt = writes.register(audio.playbackId)
                        scope.launch { writes.apply(receipt, null) }
                    }
                }
                else -> Unit
            }
        }
        scope.launch {
            core.state.collect { gate.withLock { publish() } }
        }
        with(scope) { startQueueMetadataRefresh(queue, repository) }
    }

    /** Start only after host controls are installed. History uses the same queue/command boundary. */
    fun startHistoryPlayback() {
        if (historyStarted) return
        historyStarted = true
        val saved = history.restoreFromHistory()
        val binding = if (saved != null) KoinPlatform.getKoin().get<MediaSourceBindingRepository>() else null
        historyRestorer = saved?.let {
            HistoryQueueRestorer(it, repository,
                binding!!.observeHistoryRestoreSettled(it.sourceNames.getOrNull(it.index)),
                binding!!.observeReadableSources(), preparationOwnedByPlayback = true)
        }
        historyRestorer?.start(scope, queue, ::onQueueRestored)
        with(history) { with(scope) { startRecording(this@CorePlayback, historyRestorer?.state) } }
    }

    override suspend fun onQueueRestored(snapshot: PlaybackHistory.HistorySnapshot) = withContext(scope.coroutineContext.minusKey(Job)) {
        gate.withLock {
            core.dispatch(PlaybackCommand.Seek(snapshot.position))
            if (history.historyStorage.autoPlayOnRestore()) core.dispatch(PlaybackCommand.Play)
            publish()
        }
    }

    private suspend fun claimUserControl() {
        withContext(scope.coroutineContext.minusKey(Job)) { controlRevision++ }
        historyRestorer?.claimPlaybackControl()
    }

    private suspend fun command(command: PlaybackCommand) {
        claimUserControl()
        withContext(scope.coroutineContext.minusKey(Job)) { gate.withLock { core.dispatch(command); publish() } }
    }
    suspend fun pauseForInterruption(): InterruptionToken? = withContext(scope.coroutineContext.minusKey(Job)) {
        gate.withLock {
            val state = core.state.value
            val token = state.ticket?.takeIf { state.intent == PlayIntent.Play }?.let { InterruptionToken(controlRevision, it) }
            core.dispatch(PlaybackCommand.Pause)
            publish()
            token
        }
    }
    suspend fun resumeAfterInterruption(token: InterruptionToken) = withContext(scope.coroutineContext.minusKey(Job)) {
        gate.withLock {
            if (controlRevision == token.revision && core.state.value.ticket == token.ticket) {
                core.dispatch(PlaybackCommand.Play)
                publish()
            }
        }
    }
    override suspend fun play() = command(PlaybackCommand.Play)
    override suspend fun pause() = command(PlaybackCommand.Pause)
    override suspend fun stop() = command(PlaybackCommand.Stop)
    override suspend fun skipToNext() = command(PlaybackCommand.Next)
    override suspend fun skipToPrevious() = command(PlaybackCommand.Previous)
    override suspend fun seekTo(positionMs: Long) = command(PlaybackCommand.Seek(positionMs))
    override suspend fun togglePlayPause() = if (core.state.value.intent == PlayIntent.Play) pause() else play()
    override fun currentPosition(): Long = core.state.value.positionMs
    override suspend fun historyPosition(expectedQueue: QueueState): Long? =
        currentPosition().takeIf { queueState.value === expectedQueue && core.state.value.phase != Phase.Preparing }

    override suspend fun skipTo(index: Int, start: Boolean) {
        claimUserControl()
        withContext(scope.coroutineContext.minusKey(Job)) { gate.withLock {
            val key = core.state.value.queue.getOrNull(index) ?: return@withLock
            if (key == core.state.value.selected) return@withLock
            core.dispatch(PlaybackCommand.Select(key, startPlayback = start))
            publish(picked = true)
        } }
    }

    override suspend fun updatePlaylist(playlist: List<LAudio>, startIndex: Int, start: Boolean) {
        claimUserControl()
        withContext(scope.coroutineContext.minusKey(Job)) { gate.withLock {
            playlist.forEach { items[it.key()] = it }
            core.dispatch(PlaybackCommand.ReplaceQueue(playlist.map { it.key() }, playlist.getOrNull(startIndex)?.key(), startPlayback = start))
            publish(picked = startIndex >= 0)
        } }
    }

    override suspend fun playAudio(audio: LAudio) {
        claimUserControl()
        withContext(scope.coroutineContext.minusKey(Job)) { gate.withLock {
            if (core.state.value.selected == audio.key()) return@withLock
            items[audio.key()] = audio
            core.dispatch(PlaybackCommand.Select(audio.key(), startPlayback = true))
            publish(picked = true)
        } }
    }
    override suspend fun playNext(audio: LAudio) {
        claimUserControl()
        withContext(scope.coroutineContext.minusKey(Job)) { gate.withLock {
            items[audio.key()] = audio
            core.dispatch(PlaybackCommand.PlayNext(audio.key()))
            publish()
        } }
    }
    override suspend fun setPlaybackMode(mode: PlaybackMode) = command(PlaybackCommand.SetMode(mode.toCore()))
    override suspend fun setPauseWhenCompletion(cancel: Boolean) = command(PlaybackCommand.SetPauseAtEnd(!cancel))
    override suspend fun editQueue(block: QueueUpdateRequest.() -> Unit) = editQueueWithReceipt(block) {}
    override suspend fun editQueueWithReceipt(block: QueueUpdateRequest.() -> Unit, onApplied: (QueueState) -> Unit) {
        claimUserControl()
        withContext(scope.coroutineContext.minusKey(Job)) { gate.withLock {
            publish()
            val mutation = QueueUpdateRequest(queueState.value).apply(block)
            applyQueue(mutation.build(QueueUpdateReason.Inner), mutation.replacesAll)
            onApplied(queueState.value)
        } }
    }
    override suspend fun restoreFailedQueueEdit(expected: QueueState, original: QueueState, position: Long): Boolean =
        withContext(scope.coroutineContext.minusKey(Job)) { gate.withLock {
            publish()
            if (!queueState.value.stillOwnsEdit(expected)) return@withLock false
            core.dispatch(PlaybackCommand.Pause)
            applyQueue(original)
            core.dispatch(PlaybackCommand.Seek(position))
            publish()
            true
        } }

    private fun recordFailure(audio: LAudio, reason: PlaybackFailureReason) {
        val writes = failureWrites ?: return
        if (sources.findEnabledSource(audio.mediaSourceName)?.contentState?.value?.isReady != true) return
        val receipt = writes.register(audio.playbackId)
        scope.launch { writes.apply(receipt, PlaybackFailure(reason, Clock.System.now().toEpochMilliseconds())) }
    }

    private suspend fun applyQueue(request: QueueState, replacesAll: Boolean = false) {
        val previous = queueState.value
        request.list.forEach { items[it.key()] = it }
        val keys = request.list.map { it.key() }.distinct()
        val state = core.state.value
        // Metadata refresh is not a structural edit and must not revoke a random next intent.
        if (replacesAll || keys != state.queue || request.currentItem()?.key() != state.selected) {
            val explicitlySelected = request.selectionRevision != previous.selectionRevision ||
                request.updateReason == QueueUpdateReason.HistoryRestore
            val selection = if (explicitlySelected) request.currentItem()?.key() else if (replacesAll) null else {
                state.selected?.takeIf { it in keys } ?: state.queue.drop(state.index + 1)
                    .firstOrNull { it in keys } ?: keys.firstOrNull()
            }
            core.dispatch(PlaybackCommand.ReplaceQueue(keys, selection))
        }
        queueState.value = request
        publish()
    }

    private fun publish(picked: Boolean? = null) {
        val state = core.state.value
        val old = queueState.value
        val list = state.queue.mapNotNull(items::get)
        val changed = old.currentItem()?.key() != state.selected
        queueState.value = old.copy(list = list, index = state.index.coerceAtLeast(0),
            currentPickedByUser = picked ?: if (changed) false else old.currentPickedByUser)
        playing.value = state.phase == Phase.Playing
        duration.value = state.durationMs
        engineKey.value = list.getOrNull(state.index)?.playbackId
        buffered.value = state.bufferedPositionMs
        mode.value = state.mode.toUi()
        next.value = state.explicitNext?.let(items::get)
        state.error?.let { error ->
            val stamp = state.epoch to error
            if (stamp != lastError) { failures.tryEmit(IllegalStateException(error)); lastError = stamp }
        }
        if (state.error == null) lastError = null
        if (lastEpoch != state.epoch && state.selected != null) {
            val audio = items[state.selected]
            tracker?.onMediaItemTransition(audio?.playbackId, audio?.title,
                isRepeating = !changed, isNormalTransition = changed)
            lastEpoch = state.epoch
        }
        if (lastPlaying != playing.value) { lastPlaying = playing.value; tracker?.onIsPlayingChanged(lastPlaying) }
        items.keys.retainAll(state.queue.toSet() + listOfNotNull(state.explicitNext))
    }
}

internal fun LAudio.key() = TrackKey(mediaSourceName, id)
internal fun PlaybackMode.toCore() = when (this) {
    PlaybackMode.SEQUENTIAL -> PlayMode.Sequential
    PlaybackMode.LOOP -> PlayMode.Loop
    PlaybackMode.SINGLE_LOOP -> PlayMode.RepeatOne
    PlaybackMode.SHUFFLE -> PlayMode.Random
}
internal fun PlayMode.toUi() = when (this) {
    PlayMode.Sequential -> PlaybackMode.SEQUENTIAL
    PlayMode.Loop -> PlaybackMode.LOOP
    PlayMode.RepeatOne -> PlaybackMode.SINGLE_LOOP
    PlayMode.Random -> PlaybackMode.SHUFFLE
}

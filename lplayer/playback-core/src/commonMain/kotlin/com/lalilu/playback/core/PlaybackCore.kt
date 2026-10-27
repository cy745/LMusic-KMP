package com.lalilu.playback.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/** One writer for policy/state. Network resolution never runs inside the command loop. */
class PlaybackCore(
    parentScope: CoroutineScope,
    private val resolver: ContentResolver,
    private val runtime: AudioRuntime,
    private val random: Random = Random.Default,
    initialMode: PlayMode = PlayMode.Sequential,
    private val onHistoryState: (PlaybackSnapshot, Boolean) -> Unit = { _, _ -> },
) {
    private sealed interface Message {
        data class Command(val value: PlaybackCommand, val done: CompletableDeferred<Unit>) : Message
        data class Resolved(val ticket: LoadTicket, val resource: PlayableResource) : Message
        data class CandidateResolved(val plan: NextPlan, val resource: PlayableResource) : Message
        data class Event(val value: RuntimeEvent) : Message
    }
    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job)
    private val inbox = Channel<Message>(Channel.UNLIMITED)
    private val mutable = MutableStateFlow(PlaybackSnapshot(mode = initialMode))
    val state: StateFlow<PlaybackSnapshot> = mutable.asStateFlow()
    private var s: PlaybackSnapshot
        get() = mutable.value
        set(value) { mutable.value = value }
    private var resolving: Job? = null
    private data class CandidateBasis(val ticket: LoadTicket, val queue: List<TrackKey>, val mode: PlayMode, val explicit: TrackKey?, val pauseAtEnd: Boolean)
    private var candidateBasis: CandidateBasis? = null
    private var candidatePlan: NextPlan? = null
    private var candidateResource: PlayableResource? = null
    private var candidateJob: Job? = null
    private var planSerial = 0L
    private var seekSerial = 0L
    private var direction = 1
    private var naturalTraversal = false
    private var startTransition = VolumeTransition.TrackChange
    private val attempted = mutableSetOf<TrackKey>()
    private val recent = mutableListOf<TrackKey>()
    private var startedTicket: LoadTicket? = null
    private var endedTicket: LoadTicket? = null

    init {
        runtime.onEvent = { inbox.trySend(Message.Event(it)) }
        scope.launch {
            try {
                for (message in inbox) {
                    try {
                        when (message) {
                            is Message.Command -> {
                                command(message.value)
                                if (message.value is PlaybackCommand.Select || message.value == PlaybackCommand.Next ||
                                    message.value == PlaybackCommand.Previous || message.value == PlaybackCommand.Play ||
                                    (message.value as? PlaybackCommand.ReplaceQueue)?.startPlayback == true) {
                                    s = s.copy(historyRequested = s.selected != null)
                                }
                            }
                            is Message.Resolved -> if (message.ticket == s.ticket) runtime.prepare(message.ticket, message.resource)
                            is Message.CandidateResolved -> if (candidatePlan == message.plan) {
                                try {
                                    (runtime as PreloadingAudioRuntime).stageNext(message.plan, message.resource)
                                    candidateResource = message.resource
                                } catch (failure: Exception) {
                                    // Native speculative preparation is best-effort, just like source resolution.
                                    candidateResource = null
                                    (runtime as PreloadingAudioRuntime).cancelNext()
                                }
                            }
                            is Message.Event -> event(message.value)
                        }
                        onHistoryState(s, false)
                        reconcileCandidate()
                        if (message is Message.Command) message.done.complete(Unit)
                    } catch (cancelled: CancellationException) {
                        if (message is Message.Command) message.done.cancel(cancelled)
                        throw cancelled
                    } catch (failure: Exception) {
                        s = s.copy(intent = PlayIntent.Pause, phase = Phase.Failed, error = failure.message ?: "Playback command failed")
                        onHistoryState(s, false)
                        if (message is Message.Command) message.done.completeExceptionally(failure)
                    }
                }
            } finally {
                inbox.close()
                while (true) {
                    val pending = inbox.tryReceive().getOrNull() ?: break
                    if (pending is Message.Command) pending.done.cancel()
                }
            }
        }
    }

    /** Returns once the decision is applied, not once media is ready. */
    suspend fun dispatch(command: PlaybackCommand) {
        val done = CompletableDeferred<Unit>()
        inbox.send(Message.Command(command, done))
        done.await()
    }

    /** Call on the host's runtime dispatcher; releases only this core's resources. */
    fun close() {
        onHistoryState(s.copy(phase = Phase.ReadyPaused), false)
        runtime.onEvent = null
        inbox.close()
        scope.cancel()
        runtime.release()
    }

    private fun command(command: PlaybackCommand) {
        when (command) {
            is PlaybackCommand.ReplaceQueue -> {
                replaceQueue(command)
                if (command.startPlayback) command(PlaybackCommand.Play)
            }
            is PlaybackCommand.Select -> {
                if (command.key == s.selected) return
                var items = s.queue
                if (command.key !in items) items = items.toMutableList().apply { add((s.index + 1).coerceAtLeast(0), command.key) }
                s = s.copy(queue = items, explicitNext = null)
                beginUserNavigation(1)
                select(items.indexOf(command.key))
                if (command.startPlayback) command(PlaybackCommand.Play)
            }
            is PlaybackCommand.Remove -> {
                if (command.key !in s.queue) return
                val current = s.selected
                val oldIndex = s.index
                val items = s.queue.filterNot { it == command.key }
                s = s.copy(queue = items, explicitNext = null)
                if (items.isEmpty()) empty()
                else if (current == command.key) {
                    beginUserNavigation(1)
                    select(oldIndex % items.size)
                } else s = s.copy(index = items.indexOf(current))
            }
            is PlaybackCommand.SetMode -> {
                if (command.mode != s.mode) s = s.copy(mode = command.mode, explicitNext = null)
            }
            is PlaybackCommand.SetSkipFailures -> s = s.copy(skipFailures = command.enabled)
            is PlaybackCommand.SetPauseAtEnd -> { s = s.copy(pauseAtEnd = command.enabled) }
            is PlaybackCommand.PlayNext -> playNext(command.key)
            PlaybackCommand.Next -> { beginUserNavigation(1); move(1, natural = false) }
            PlaybackCommand.Previous -> { beginUserNavigation(-1); s = s.copy(explicitNext = null); move(-1, natural = false) }
            PlaybackCommand.SystemPause -> pause(VolumeTransition.Immediate)
            PlaybackCommand.Pause -> {
                pause(VolumeTransition.UserControl)
            }
            PlaybackCommand.Stop -> {
                cancelCandidate()
                resolving?.cancel()
                runtime.silence(null)
                s = s.copy(intent = PlayIntent.Pause, phase = if (s.selected == null) Phase.Empty else Phase.Ended,
                    epoch = s.epoch + 1, pendingSeekId = null, positionMs = 0, historyRequested = false)
            }
            PlaybackCommand.Play -> {
                if (s.selected == null) return
                val phase = s.phase
                s = s.copy(intent = PlayIntent.Play)
                if (phase == Phase.Failed || phase == Phase.Ended) {
                    beginUserNavigation(1)
                    select(s.index)
                    startTransition = VolumeTransition.UserControl
                } else if (phase == Phase.Preparing) {
                    startTransition = VolumeTransition.UserControl
                } else if (phase != Phase.Preparing && phase != Phase.Seeking && phase != Phase.Playing) {
                    s = s.copy(phase = Phase.Starting)
                    runtime.setPlaying(s.ticket!!, true)
                }
            }
            is PlaybackCommand.Seek -> {
                if (s.phase !in setOf(Phase.Playing, Phase.ReadyPaused, Phase.Starting, Phase.Seeking, Phase.Buffering, Phase.Ended, Phase.Preparing, Phase.Suppressed)) return
                val target = command.positionMs.coerceAtLeast(0).let { if (s.durationMs > 0) it.coerceAtMost(s.durationMs) else it }
                if (s.phase == Phase.Ended) select(s.index)
                val seekId = ++seekSerial
                val preparing = s.phase == Phase.Preparing
                s = s.copy(phase = if (preparing) Phase.Preparing else Phase.Seeking, pendingSeekId = seekId, positionMs = target, bufferedPositionMs = runtime.bufferedPositionAt(s.ticket!!, target))
                if (!preparing) runtime.seek(s.ticket!!, seekId, target)
            }
        }
    }

    private fun replaceQueue(command: PlaybackCommand.ReplaceQueue) {
        val current = s.selected
        val wasEmpty = s.queue.isEmpty()
        val items = command.items.distinct()
        val target = command.selected?.takeIf { it in items } ?: current?.takeIf { it in items } ?: items.firstOrNull()
        s = s.copy(queue = items, explicitNext = null, intent = if (wasEmpty) PlayIntent.Pause else s.intent)
        if (items.isEmpty()) { empty(); return }
        val index = items.indexOf(target)
        if (current == target) s = s.copy(index = index)
        else { beginUserNavigation(1); select(index) }
    }

    private fun playNext(key: TrackKey) {
        if (key == s.selected) return
        if (s.queue.isEmpty()) { replaceQueue(PlaybackCommand.ReplaceQueue(listOf(key))); return }
        if (s.mode == PlayMode.Random) {
            // The explicit intent is separate from the actual queue until selection.
            s = s.copy(explicitNext = key)
        } else {
            val current = s.selected
            val items = s.queue.filterNot { it == key }.toMutableList()
            val index = items.indexOf(current)
            items.add(index + 1, key)
            s = s.copy(queue = items, index = index)
        }
    }

    private fun beginUserNavigation(value: Int) {
        // A temporary interruption may auto-resume only the untouched selection.
        // Navigation revokes that implicit permission; an explicit Play can grant it again.
        if (s.phase == Phase.Suppressed) pause(VolumeTransition.Immediate)
        direction = value
        naturalTraversal = false
        attempted.clear()
    }

    private fun select(index: Int, natural: Boolean = false) {
        val cachedResource = candidateResource.takeIf { natural && candidatePlan?.next?.key == s.queue.getOrNull(index) }
        cancelCandidate()
        startTransition = if (natural) VolumeTransition.Immediate else VolumeTransition.TrackChange
        resolving?.cancel()
        s = s.copy(index = index, epoch = s.epoch + 1, naturalTransition = natural, historyRequested = natural || s.intent == PlayIntent.Play, phase = Phase.Preparing,
            positionMs = 0, durationMs = 0, bufferedPositionMs = 0, pendingSeekId = null, error = null)
        startedTicket = null
        endedTicket = null
        val ticket = s.ticket ?: return
        runtime.silence(ticket)
        resolving = scope.launch {
            try {
                val resource = cachedResource ?: resolver.resolve(ticket.key)
                inbox.send(Message.Resolved(ticket, resource))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { inbox.send(Message.Event(RuntimeEvent.Failed(ticket, failure.message ?: "Content unavailable"))) }
        }
    }

    private fun empty() {
        cancelCandidate()
        resolving?.cancel()
        attempted.clear()
        recent.clear()
        s = PlaybackSnapshot(mode = s.mode, epoch = s.epoch + 1, skipFailures = s.skipFailures)
        runtime.silence(null)
    }

    private fun move(step: Int, natural: Boolean, failed: Boolean = false) {
        if (s.queue.isEmpty()) return
        direction = step
        naturalTraversal = natural
        if (natural && s.mode == PlayMode.RepeatOne && !failed) { select(s.index, natural = true); return }
        if (s.mode == PlayMode.Random && step > 0) {
            val explicit = s.explicitNext?.takeIf { it !in attempted }
            val key = explicit ?: candidatePlan?.next?.key?.takeIf { natural && !failed } ?: randomCandidate() ?: return finishFailure()
            var items = s.queue.toMutableList()
            if (key !in items) items.add(key)
            val target = (s.index - 1 + items.size) % items.size
            val candidate = items.indexOf(key)
            val swapped = items[target]
            items[target] = key
            items[candidate] = swapped
            s = s.copy(queue = items)
            select(target, natural)
            return
        }
        // Random next moves upward; previous revisits the item below current.
        val queueStep = if (s.mode == PlayMode.Random) -step else step
        for (offset in 1..s.queue.size) {
            val raw = s.index + queueStep * offset
            if (natural && s.mode == PlayMode.Sequential && raw >= s.queue.size) {
                if (failed) finishFailure() else s = s.copy(phase = Phase.Ended, intent = PlayIntent.Pause)
                return
            }
            val index = ((raw % s.queue.size) + s.queue.size) % s.queue.size
            if (!failed || s.queue[index] !in attempted) { select(index, natural); return }
        }
        finishFailure()
    }

    private fun randomCandidate(): TrackKey? {
        val remaining = s.queue.filterNot { it in attempted }
        if (s.queue.size == 1) return remaining.firstOrNull()
        val candidates = remaining.filterNot { it == s.selected }
        if (candidates.isEmpty()) return null
        for (avoid in recent.size downTo 0) {
            val eligible = candidates.filterNot { it in recent.takeLast(avoid) }
            if (eligible.isNotEmpty()) return eligible.random(random)
        }
        return null
    }

    private fun event(event: RuntimeEvent) {
        if (event.ticket != s.ticket) return
        when (event) {
            is RuntimeEvent.Transitioned -> {
                if (candidatePlan != event.plan) return
                if (s.pendingSeekId != null) {
                    val position = s.positionMs
                    select(s.index)
                    command(PlaybackCommand.Seek(position))
                    return
                }
                if (endedTicket != s.ticket) onHistoryState(s, true)
                (runtime as PreloadingAudioRuntime).confirmNext(event.plan)
                val key = event.plan.next.key
                val items = s.queue.toMutableList()
                val index = if (s.mode == PlayMode.Random) {
                    if (key !in items) items.add(key)
                    val target = (s.index - 1 + items.size) % items.size
                    val source = items.indexOf(key)
                    val displaced = items[target]
                    items[target] = key
                    items[source] = displaced
                    target
                } else if (s.mode == PlayMode.RepeatOne) s.index else items.indexOf(key)
                candidateJob?.cancel()
                candidatePlan = null
                candidateResource = null
                candidateBasis = null
                startedTicket = null
                endedTicket = null
                direction = 1
                naturalTraversal = true
                s = s.copy(queue = items, index = index, epoch = event.plan.next.epoch, naturalTransition = true, historyRequested = true,
                    phase = if (s.intent == PlayIntent.Play) Phase.Starting else Phase.ReadyPaused,
                    positionMs = event.positionMs, durationMs = event.durationMs, bufferedPositionMs = event.positionMs,
                    pendingSeekId = null, error = null)
                if (s.intent == PlayIntent.Pause) runtime.setPlaying(event.plan.next, false, transition = VolumeTransition.Immediate)
                else if (event.playing) event(RuntimeEvent.Started(event.plan.next))
            }
            is RuntimeEvent.PausedBySystem -> pause(VolumeTransition.Immediate)
            is RuntimeEvent.Suppressed -> if (s.intent == PlayIntent.Play) s = s.copy(phase = Phase.Suppressed)
            is RuntimeEvent.Prepared -> {
                if (s.phase != Phase.Preparing) return
                val seek = s.pendingSeekId
                if (seek != null) {
                    s = s.copy(durationMs = event.durationMs.coerceAtLeast(0), phase = Phase.Seeking)
                    runtime.seek(event.ticket, seek, s.positionMs.coerceAtMost(event.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE))
                    return
                }
                s = s.copy(durationMs = event.durationMs.coerceAtLeast(0), phase = if (s.intent == PlayIntent.Play) Phase.Starting else Phase.ReadyPaused)
                runtime.setPlaying(event.ticket, s.intent == PlayIntent.Play, transition = startTransition)
            }
            is RuntimeEvent.Started -> {
                if (s.intent != PlayIntent.Play || s.phase == Phase.Failed || s.phase == Phase.Ended) {
                    runtime.setPlaying(event.ticket, false, VolumeTransition.Immediate)
                    return
                }
                if (startedTicket != event.ticket) {
                    startedTicket = event.ticket
                    attempted.clear()
                    recent.remove(event.ticket.key)
                    recent.add(event.ticket.key)
                    while (recent.size > 3) recent.removeAt(0)
                    if (s.explicitNext == event.ticket.key) s = s.copy(explicitNext = null)
                }
                if (s.pendingSeekId == null) s = s.copy(phase = Phase.Playing)
            }
            is RuntimeEvent.Position -> {
                if (s.phase == Phase.Failed || s.phase == Phase.Preparing) return
                s = s.copy(positionMs = if (s.pendingSeekId == null) event.positionMs.coerceAtLeast(0) else s.positionMs,
                    durationMs = event.durationMs.takeIf { it > 0 } ?: s.durationMs,
                    bufferedPositionMs = if (s.pendingSeekId == null) event.bufferedPositionMs.coerceAtLeast(0) else runtime.bufferedPositionAt(event.ticket, s.positionMs))
            }
            is RuntimeEvent.SeekSettled -> if (event.seekId == s.pendingSeekId) {
                s = s.copy(pendingSeekId = null, positionMs = event.positionMs.coerceAtLeast(0),
                    phase = if (s.intent == PlayIntent.Play) Phase.Starting else Phase.ReadyPaused)
                runtime.setPlaying(event.ticket, s.intent == PlayIntent.Play)
            }
            is RuntimeEvent.Buffering -> if (s.intent == PlayIntent.Play && s.phase != Phase.Preparing && s.pendingSeekId == null) s = s.copy(phase = Phase.Buffering)
            is RuntimeEvent.Failed -> {
                if (s.phase == Phase.Failed) return
                attempted.add(event.ticket.key)
                s = s.copy(error = event.message, explicitNext = null, pendingSeekId = null)
                if (s.intent == PlayIntent.Pause || s.mode == PlayMode.RepeatOne || !s.skipFailures) finishFailure()
                else move(direction, naturalTraversal, failed = true)
            }
            is RuntimeEvent.Ended -> {
                if (endedTicket == event.ticket || s.phase == Phase.Failed) return
                endedTicket = event.ticket
                onHistoryState(s, true)
                if (s.pauseAtEnd) {
                    s = s.copy(intent = PlayIntent.Pause, phase = Phase.Ended, pauseAtEnd = false)
                    return
                }
                if (s.intent == PlayIntent.Play) move(1, natural = true)
                else s = s.copy(phase = Phase.Ended)
            }
        }
    }

    private fun cancelCandidate() {
        candidateJob?.cancel()
        candidateJob = null
        candidatePlan = null
        candidateResource = null
        candidateBasis = null
        (runtime as? PreloadingAudioRuntime)?.cancelNext()
    }

    private fun reconcileCandidate() {
        if (runtime !is PreloadingAudioRuntime || !runtime.supportsPreloading) return
        val ticket = s.ticket
        if (ticket == null || s.phase in setOf(Phase.Empty, Phase.Preparing, Phase.Failed, Phase.Ended) || s.pauseAtEnd) {
            if (candidateBasis != null) cancelCandidate()
            return
        }
        val basis = CandidateBasis(ticket, s.queue, s.mode, s.explicitNext, s.pauseAtEnd)
        if (basis == candidateBasis) return
        cancelCandidate()
        candidateBasis = basis
        val key = when (s.mode) {
            PlayMode.RepeatOne -> ticket.key
            PlayMode.Sequential -> s.queue.getOrNull(s.index + 1)
            PlayMode.Loop -> s.queue[(s.index + 1) % s.queue.size]
            PlayMode.Random -> s.explicitNext ?: randomCandidate()
        } ?: return
        val plan = NextPlan(++planSerial, ticket, LoadTicket(key, ticket.epoch + 1))
        candidatePlan = plan
        candidateJob = scope.launch {
            try {
                inbox.send(Message.CandidateResolved(plan, resolver.resolve(key)))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                // Speculative failure is not a playback failure. Retry only when this item is selected.
            }
        }
    }

    private fun pause(transition: VolumeTransition) {
        s = s.copy(intent = PlayIntent.Pause,
            phase = if (s.phase in setOf(Phase.Playing, Phase.Starting, Phase.Buffering, Phase.Suppressed)) Phase.ReadyPaused else s.phase)
        s.ticket?.let { runtime.setPlaying(it, false, transition) }
    }

    private fun finishFailure() {
        s = s.copy(phase = Phase.Failed, intent = PlayIntent.Pause, pendingSeekId = null,
            error = s.error ?: "No playable candidate")
        s.ticket?.let { runtime.setPlaying(it, false, VolumeTransition.Immediate) }
    }
}

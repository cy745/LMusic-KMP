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
) {
    private sealed interface Message {
        data class Command(val value: PlaybackCommand, val done: CompletableDeferred<Unit>) : Message
        data class Resolved(val ticket: LoadTicket, val resource: PlayableResource) : Message
        data class Event(val value: RuntimeEvent) : Message
    }
    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job)
    private val inbox = Channel<Message>(Channel.UNLIMITED)
    private val mutable = MutableStateFlow(PlaybackSnapshot())
    val state: StateFlow<PlaybackSnapshot> = mutable.asStateFlow()
    private var s: PlaybackSnapshot
        get() = mutable.value
        set(value) { mutable.value = value }
    private var resolving: Job? = null
    private var seekSerial = 0L
    private var direction = 1
    private var naturalTraversal = false
    private var fadeOnStart = true
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
                            is Message.Command -> command(message.value)
                            is Message.Resolved -> if (message.ticket == s.ticket) runtime.prepare(message.ticket, message.resource)
                            is Message.Event -> event(message.value)
                        }
                        if (message is Message.Command) message.done.complete(Unit)
                    } catch (cancelled: CancellationException) {
                        if (message is Message.Command) message.done.cancel(cancelled)
                        throw cancelled
                    } catch (failure: Exception) {
                        s = s.copy(intent = PlayIntent.Pause, phase = Phase.Failed, error = failure.message ?: "Playback command failed")
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
        runtime.onEvent = null
        inbox.close()
        scope.cancel()
        runtime.release()
    }

    private fun command(command: PlaybackCommand) {
        when (command) {
            is PlaybackCommand.ReplaceQueue -> replaceQueue(command)
            is PlaybackCommand.Select -> {
                if (command.key == s.selected) return
                var items = s.queue
                if (command.key !in items) items = items.toMutableList().apply { add((s.index + 1).coerceAtLeast(0), command.key) }
                s = s.copy(queue = items, explicitNext = null)
                beginUserNavigation(1)
                select(items.indexOf(command.key))
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
            is PlaybackCommand.PlayNext -> playNext(command.key)
            PlaybackCommand.Next -> { beginUserNavigation(1); move(1, natural = false) }
            PlaybackCommand.Previous -> { beginUserNavigation(-1); s = s.copy(explicitNext = null); move(-1, natural = false) }
            PlaybackCommand.Pause -> {
                s = s.copy(intent = PlayIntent.Pause,
                    phase = if (s.phase in setOf(Phase.Playing, Phase.Starting, Phase.Buffering)) Phase.ReadyPaused else s.phase)
                s.ticket?.let { runtime.setPlaying(it, false) }
            }
            PlaybackCommand.Play -> {
                if (s.selected == null) return
                val phase = s.phase
                s = s.copy(intent = PlayIntent.Play)
                if (phase == Phase.Failed || phase == Phase.Ended) {
                    beginUserNavigation(1)
                    select(s.index)
                } else if (phase != Phase.Preparing && phase != Phase.Seeking && phase != Phase.Playing) {
                    s = s.copy(phase = Phase.Starting)
                    runtime.setPlaying(s.ticket!!, true)
                }
            }
            is PlaybackCommand.Seek -> {
                if (s.phase !in setOf(Phase.Playing, Phase.ReadyPaused, Phase.Starting, Phase.Seeking, Phase.Buffering, Phase.Ended)) return
                val target = command.positionMs.coerceAtLeast(0).let { if (s.durationMs > 0) it.coerceAtMost(s.durationMs) else it }
                val seekId = ++seekSerial
                s = s.copy(phase = Phase.Seeking, pendingSeekId = seekId, positionMs = target, bufferedPositionMs = 0)
                runtime.seek(s.ticket!!, seekId, target)
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
        direction = value
        naturalTraversal = false
        attempted.clear()
    }

    private fun select(index: Int, natural: Boolean = false) {
        fadeOnStart = !natural
        resolving?.cancel()
        s = s.copy(index = index, epoch = s.epoch + 1, phase = Phase.Preparing,
            positionMs = 0, durationMs = 0, bufferedPositionMs = 0, pendingSeekId = null, error = null)
        startedTicket = null
        endedTicket = null
        val ticket = s.ticket ?: return
        runtime.silence(ticket)
        resolving = scope.launch {
            try {
                val resource = resolver.resolve(ticket.key)
                inbox.send(Message.Resolved(ticket, resource))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { inbox.send(Message.Event(RuntimeEvent.Failed(ticket, failure.message ?: "Content unavailable"))) }
        }
    }

    private fun empty() {
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
            val key = explicit ?: randomCandidate() ?: return finishFailure()
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
            is RuntimeEvent.Prepared -> {
                if (s.phase != Phase.Preparing) return
                s = s.copy(durationMs = event.durationMs.coerceAtLeast(0), phase = if (s.intent == PlayIntent.Play) Phase.Starting else Phase.ReadyPaused)
                runtime.setPlaying(event.ticket, s.intent == PlayIntent.Play, smooth = fadeOnStart)
            }
            is RuntimeEvent.Started -> {
                if (s.intent != PlayIntent.Play || s.phase == Phase.Failed || s.phase == Phase.Ended) {
                    runtime.setPlaying(event.ticket, false)
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
                    bufferedPositionMs = if (s.pendingSeekId == null) event.bufferedPositionMs.coerceAtLeast(0) else 0)
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
                if (s.intent == PlayIntent.Play) move(1, natural = true)
                else s = s.copy(phase = Phase.Ended)
            }
        }
    }

    private fun finishFailure() {
        s = s.copy(phase = Phase.Failed, intent = PlayIntent.Pause, pendingSeekId = null,
            error = s.error ?: "No playable candidate")
        s.ticket?.let { runtime.setPlaying(it, false) }
    }
}

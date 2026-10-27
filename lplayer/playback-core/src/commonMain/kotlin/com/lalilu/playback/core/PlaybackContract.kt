package com.lalilu.playback.core

/** Source-qualified identity. URLs and display names are deliberately not identities. */
data class TrackKey(val source: String, val id: String)
data class LoadTicket(val key: TrackKey, val epoch: Long)
data class PlayableResource(val uri: String)

enum class PlayIntent { Play, Pause }
enum class PlayMode { Sequential, Loop, RepeatOne, Random }
enum class Phase { Empty, Preparing, ReadyPaused, Starting, Playing, Seeking, Buffering, Suppressed, Failed, Ended }

data class PlaybackSnapshot(
    val queue: List<TrackKey> = emptyList(),
    val index: Int = -1,
    val intent: PlayIntent = PlayIntent.Pause,
    val phase: Phase = Phase.Empty,
    val mode: PlayMode = PlayMode.Sequential,
    val epoch: Long = 0,
    /** Cause of this selection epoch, retained for platform timeline/history projections. */
    val naturalTransition: Boolean = false,
    /** Actual selection intent, excluding silent restoration and speculative preparation. */
    val historyRequested: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedPositionMs: Long = 0,
    val pendingSeekId: Long? = null,
    val explicitNext: TrackKey? = null,
    val error: String? = null,
    val skipFailures: Boolean = true,
    val pauseAtEnd: Boolean = false,
) {
    val selected: TrackKey? get() = queue.getOrNull(index)
    val ticket: LoadTicket? get() = selected?.let { LoadTicket(it, epoch) }
}

sealed interface PlaybackCommand {
    data class ReplaceQueue(val items: List<TrackKey>, val selected: TrackKey? = null, val startPlayback: Boolean = false) : PlaybackCommand
    data class Select(val key: TrackKey, val startPlayback: Boolean = false) : PlaybackCommand
    data class Remove(val key: TrackKey) : PlaybackCommand
    data class PlayNext(val key: TrackKey) : PlaybackCommand
    data class SetMode(val mode: PlayMode) : PlaybackCommand
    data class SetSkipFailures(val enabled: Boolean) : PlaybackCommand
    data class SetPauseAtEnd(val enabled: Boolean) : PlaybackCommand
    data class Seek(val positionMs: Long) : PlaybackCommand
    data object Play : PlaybackCommand
    data object Pause : PlaybackCommand
    data object SystemPause : PlaybackCommand
    data object Stop : PlaybackCommand
    data object Next : PlaybackCommand
    data object Previous : PlaybackCommand
}

fun interface ContentResolver {
    suspend fun resolve(key: TrackKey): PlayableResource
}

/** Fast, nonblocking commands. Implementations own native-thread confinement and teardown. */
interface AudioRuntime {
    var onEvent: ((RuntimeEvent) -> Unit)?
    /** Invalidates prior output ownership immediately; its short fade must not block commands. */
    fun silence(next: LoadTicket?)
    fun prepare(ticket: LoadTicket, resource: PlayableResource)
    fun setPlaying(ticket: LoadTicket, playing: Boolean, transition: VolumeTransition = VolumeTransition.UserControl)
    /** Actual coverage at a seek target, before changing the decoder position. Unknown means no lookahead. */
    fun bufferedPositionAt(ticket: LoadTicket, positionMs: Long): Long = positionMs
    fun seek(ticket: LoadTicket, seekId: Long, positionMs: Long)
    fun release()
}

sealed interface RuntimeEvent {
    val ticket: LoadTicket
    data class Transitioned(val plan: NextPlan, val durationMs: Long, val positionMs: Long, val playing: Boolean) : RuntimeEvent {
        override val ticket: LoadTicket get() = plan.from
    }
    data class PausedBySystem(override val ticket: LoadTicket) : RuntimeEvent
    data class Suppressed(override val ticket: LoadTicket) : RuntimeEvent
    data class Prepared(override val ticket: LoadTicket, val durationMs: Long) : RuntimeEvent
    data class Started(override val ticket: LoadTicket) : RuntimeEvent
    data class Position(override val ticket: LoadTicket, val positionMs: Long, val durationMs: Long, val bufferedPositionMs: Long) : RuntimeEvent
    data class SeekSettled(override val ticket: LoadTicket, val seekId: Long, val positionMs: Long) : RuntimeEvent
    data class Buffering(override val ticket: LoadTicket) : RuntimeEvent
    data class Failed(override val ticket: LoadTicket, val message: String) : RuntimeEvent
    data class Ended(override val ticket: LoadTicket) : RuntimeEvent
}

/** Private, revocable execution authorization; never part of the visible queue snapshot. */
data class NextPlan(val serial: Long, val from: LoadTicket, val next: LoadTicket)

/** Optional capability. A runtime without continuous output keeps the normal Ended/prepare path. */
interface PreloadingAudioRuntime : AudioRuntime {
    val supportsPreloading: Boolean get() = true
    fun stageNext(plan: NextPlan, resource: PlayableResource)
    fun cancelNext()
    /** Acknowledge the native boundary only after the core accepts its still-current plan. */
    fun confirmNext(plan: NextPlan)
}

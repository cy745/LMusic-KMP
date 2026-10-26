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
    data class ReplaceQueue(val items: List<TrackKey>, val selected: TrackKey? = null) : PlaybackCommand
    data class Select(val key: TrackKey) : PlaybackCommand
    data class Remove(val key: TrackKey) : PlaybackCommand
    data class PlayNext(val key: TrackKey) : PlaybackCommand
    data class SetMode(val mode: PlayMode) : PlaybackCommand
    data class SetSkipFailures(val enabled: Boolean) : PlaybackCommand
    data class SetPauseAtEnd(val enabled: Boolean) : PlaybackCommand
    data class Seek(val positionMs: Long) : PlaybackCommand
    data object Play : PlaybackCommand
    data object Pause : PlaybackCommand
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
    fun setPlaying(ticket: LoadTicket, playing: Boolean, smooth: Boolean = true)
    fun seek(ticket: LoadTicket, seekId: Long, positionMs: Long)
    fun release()
}

sealed interface RuntimeEvent {
    val ticket: LoadTicket
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

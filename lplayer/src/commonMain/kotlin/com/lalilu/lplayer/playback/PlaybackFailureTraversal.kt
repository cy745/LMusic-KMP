package com.lalilu.lplayer.playback

/** One navigation attempt owns its direction and visited slots until success or user takeover. */
internal class PlaybackFailureTraversal {
    var generation: Long = 0
        private set
    var direction: PlaybackDirection = PlaybackDirection.Forward
        private set
    private val visited = mutableSetOf<Int>()
    private val attemptedIds = mutableSetOf<String>()
    private var queueIds: List<String>? = null

    /** Invalidate old positional work, but keep attempted identities across shuffle permutations. */
    fun observeQueue(ids: List<String>) {
        if (queueIds == ids) return
        queueIds = ids.toList()
        generation++
        visited.clear()
    }

    fun begin(direction: PlaybackDirection = PlaybackDirection.Forward): Long {
        generation++
        this.direction = direction
        visited.clear()
        attemptedIds.clear()
        return generation
    }

    fun cancel() { begin(direction) }

    fun next(ticket: Long, size: Int, failedIndex: Int, mode: PlaybackMode, playable: (Int) -> Boolean): Int? {
        if (ticket != generation) return null
        visited += failedIndex
        queueIds?.getOrNull(failedIndex)?.let(attemptedIds::add)
        return fallbackPlaybackIndices(size, failedIndex, mode, direction)
            .firstOrNull { it !in visited && queueIds?.getOrNull(it) !in attemptedIds && playable(it) }
            ?.also {
                visited += it
                queueIds?.getOrNull(it)?.let(attemptedIds::add)
            }
    }
}

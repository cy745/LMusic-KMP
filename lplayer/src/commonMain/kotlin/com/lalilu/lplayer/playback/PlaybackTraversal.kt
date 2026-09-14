package com.lalilu.lplayer.playback

/** Direction is captured when navigation starts, not inferred again from an error callback. */
internal enum class PlaybackDirection { Forward, Backward }

/** Ordered fallback slots, excluding the failed slot; at most one pass even in loop modes. */
internal fun fallbackPlaybackIndices(
    size: Int,
    current: Int,
    mode: PlaybackMode,
    direction: PlaybackDirection,
): List<Int> {
    if (size <= 1 || current !in 0 until size) return emptyList()
    val step = (if (direction == PlaybackDirection.Forward) 1 else -1) *
        (if (mode == PlaybackMode.SHUFFLE) -1 else 1)
    val wraps = mode == PlaybackMode.LOOP || mode == PlaybackMode.SHUFFLE
    return buildList {
        var index = current
        repeat(size - 1) {
            index += step
            if (index !in 0 until size) {
                if (!wraps) return@buildList
                index = if (index < 0) size - 1 else 0
            }
            add(index)
        }
    }
}

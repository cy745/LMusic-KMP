package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.MediaKey

/** Explicit user requests outrank random selection. New requests play first, without duplicates. */
internal class PlayNextRequests {
    private val pending = ArrayDeque<MediaKey>()

    fun enqueue(key: MediaKey) {
        pending.remove(key)
        pending.addFirst(key)
    }

    fun take(available: Set<MediaKey>): MediaKey? {
        while (pending.isNotEmpty()) {
            val key = pending.removeFirst()
            if (key in available) return key
        }
        return null
    }

    fun clear() = pending.clear()

    /** Apply immediately after a stable queue edit, so removing then re-adding cannot revive a request. */
    fun retain(available: Set<MediaKey>) {
        pending.removeAll { it !in available }
    }
}

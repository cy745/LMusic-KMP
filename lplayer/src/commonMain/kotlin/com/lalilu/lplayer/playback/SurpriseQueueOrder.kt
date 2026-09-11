package com.lalilu.lplayer.playback

import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * LMusic's reverse carousel: a new random song occupies the slot before current. After moving
 * there, rearrange() shows the new song first followed by the previously playing song.
 * Kept identical to Android QueueControlPlayer's distance rule for normal-sized queues.
 */
internal object SurpriseQueueOrder {
    fun nextIndex(size: Int, current: Int): Int =
        if (size == 0) -1 else (current - 1 + size) % size

    fun previousIndex(size: Int, current: Int): Int =
        if (size == 0) -1 else (current + 1) % size

    fun candidateIndex(size: Int, current: Int, random: Random = Random.Default): Int {
        if (size <= 1) return -1
        val maxIndex = size - 1
        return (0..maxIndex).filter { index ->
            index != current && min(abs(index - current), abs(maxIndex - current + index)) /
                maxIndex.toFloat() > 0.25f
        }.randomOrNull(random) ?: nextIndex(size, current)
    }
}

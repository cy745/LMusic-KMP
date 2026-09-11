package com.lalilu.lplayer.playback

import io.github.petertrr.diffutils.algorithm.myers.MyersDiff
import io.github.petertrr.diffutils.patch.DeltaType

internal data class QueueReplacement<T>(val from: Int, val to: Int, val items: List<T>)

/** Original indices remain valid only when patches are applied from the end. */
internal fun <T> queueReplacements(old: List<T>, new: List<T>): List<QueueReplacement<T>> =
    MyersDiff<T>().computeDiff(old, new)
        .filter { it.deltaType != DeltaType.EQUAL }
        .sortedByDescending { it.startOriginal }
        .map { QueueReplacement(it.startOriginal, it.endOriginal, new.subList(it.startRevised, it.endRevised)) }

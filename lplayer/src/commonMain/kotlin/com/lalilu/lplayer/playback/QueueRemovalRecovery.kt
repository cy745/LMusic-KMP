package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.lmedia.domain.model.mediaKey

/** One deletion owns one receipt, including when the platform throws after publishing removal. */
class QueueRemovalRecovery(private val playback: Playback, private val keys: Set<MediaKey>) {
    private data class Receipt(val original: QueueState, val removed: QueueState, val position: Long)
    private var receipt: Receipt? = null

    suspend fun remove() {
        check(receipt == null) { "Queue removal already started" }
        val sampled = playback.queue.stateSnapshot()
        val position = playback.sampleHistoryPosition() ?: 0L
        playback.editQueueWithReceipt(block = {
            val before = playback.queue.stateSnapshot()
            if (before.list.any { it.mediaKey in keys }) {
                removeAll(keys)
                // Captured before native application: an exception after publication still has an undo.
                receipt = Receipt(before, build(QueueUpdateReason.Inner),
                    if (before === sampled) position else 0L)
            }
        }, onApplied = { applied ->
            receipt?.let { saved ->
                // Accept internal selection acknowledgements, never a different native selection.
                if (applied.index == saved.removed.index &&
                    applied.list.map { it.mediaKey } == saved.removed.list.map { it.mediaKey }) {
                    receipt = saved.copy(removed = applied)
                }
            }
        })
        check(playback.queue.stateSnapshot().list.none { it.mediaKey in keys }) {
            "歌曲仍在播放队列中，删除已取消"
        }
    }

    suspend fun restore(restoredAudios: List<LAudio>): Boolean {
        val saved = receipt ?: return false
        val fresh = playback.queue.stateSnapshot().list.associateBy { it.mediaKey } +
            restoredAudios.associateBy { it.mediaKey }
        val original = saved.original.copy(list = saved.original.list.map { fresh[it.mediaKey] ?: it })
        return playback.restoreFailedQueueEdit(saved.removed, original, saved.position)
    }
}

/** Metadata/native acknowledgements are allowed; a user edit, even if undone later, is not. */
internal fun QueueState.stillOwnsEdit(expected: QueueState): Boolean =
    editRevision == expected.editRevision && selectionRevision == expected.selectionRevision &&
        index == expected.index && list.map { it.mediaKey } == expected.list.map { it.mediaKey }

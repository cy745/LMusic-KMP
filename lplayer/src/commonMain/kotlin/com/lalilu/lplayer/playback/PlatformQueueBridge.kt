package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes app edits with platform mirrors; database lookups stay outside the lock. */
internal class PlatformQueueBridge(
    private val backing: PlayableQueue = PlayableQueueImpl(),
    private val applyToPlatform: suspend (QueueState) -> Unit,
    private val readPlatformAfterApply: () -> Unit,
) {
    private val mutex = Mutex()
    private val generation = MutableStateFlow(0L)

    fun newPlatformSnapshot(): Long {
        while (true) {
            val old = generation.value
            if (generation.compareAndSet(old, old + 1)) return old + 1
        }
    }

    val queue: PlayableQueue = object : PlayableQueue by backing {
        override suspend fun update(
            updateReason: QueueUpdateReason,
            predicate: (QueueState) -> Boolean,
            block: QueueUpdateRequest.() -> Unit,
        ) = mutex.withLock {
            val before = backing.stateSnapshot()
            backing.update(updateReason, predicate, block)
            if (backing.stateSnapshot() === before || updateReason == QueueUpdateReason.Sync) {
                return@withLock
            }
            newPlatformSnapshot()
            try {
                applyToPlatform(backing.stateSnapshot())
            } finally {
                // Discard intermediate Timeline callbacks produced by the patch itself.
                newPlatformSnapshot()
                readPlatformAfterApply()
            }
        }
    }

    /** No platform callback can change the selected index between insertion and the seek. */
    suspend fun editAndRun(
        block: QueueUpdateRequest.() -> Unit,
        command: suspend (QueueState) -> Unit,
    ) = mutex.withLock {
        newPlatformSnapshot()
        try {
            backing.update(block = block)
            val selected = backing.stateSnapshot()
            applyToPlatform(selected)
            command(selected)
        } finally {
            newPlatformSnapshot()
            readPlatformAfterApply()
        }
    }

    suspend fun acceptPlatformSnapshot(ticket: Long, items: List<LAudio>, index: Int) = mutex.withLock {
        backing.update(QueueUpdateReason.Sync, predicate = { generation.value == ticket }) {
            replaceAll(items, index)
        }
    }
}

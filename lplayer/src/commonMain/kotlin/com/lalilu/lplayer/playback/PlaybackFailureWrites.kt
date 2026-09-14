package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.PlaybackFailure
import com.lalilu.lmedia.domain.repository.PlaybackFailureRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Register at event arrival, before launching asynchronous work. Latest event wins per song. */
internal class PlaybackFailureWrites(private val repository: PlaybackFailureRepository) {
    data class Ticket(val id: String, val revision: Long)
    private val revisions = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val writes = Mutex()

    fun register(id: String): Ticket {
        while (true) {
            val before = revisions.value
            val revision = (before[id] ?: 0L) + 1L
            if (revisions.compareAndSet(before, before + (id to revision))) return Ticket(id, revision)
        }
    }

    /** Persistence must not abort navigation. Cancellation still belongs to the caller. */
    suspend fun apply(ticket: Ticket, failure: PlaybackFailure?): Result<Unit> = try {
        writes.withLock {
            if (revisions.value[ticket.id] == ticket.revision) {
                if (failure == null) repository.clearAfterSuccessfulPlayback(ticket.id)
                else repository.record(ticket.id, failure)
            }
        }
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}

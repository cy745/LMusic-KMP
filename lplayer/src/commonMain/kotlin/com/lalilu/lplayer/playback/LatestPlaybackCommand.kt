package com.lalilu.lplayer.playback

import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

/**
 * Confined to the owning player's single dispatcher (Main on iOS). Not reentrant:
 * command implementations must call private helpers, not another public command.
 * Wait for cancelled work's cleanup before allowing a new command to touch the engine.
 */
internal class LatestPlaybackCommand {
    private var active: Job? = null
    /** Read only on the same dispatcher as run(). Includes predecessor cleanup. */
    val isRunning: Boolean get() = active != null

    suspend fun <T> run(block: suspend () -> T): T = coroutineScope {
        coroutineContext.ensureActive()
        val owner = coroutineContext.job
        val previous = active
        active = owner
        try {
            // Keep the predecessor chain intact even if a third command cancels us
            // while the first command is still releasing its native resources.
            withContext(NonCancellable) { previous?.cancelAndJoin() }
            coroutineContext.ensureActive()
            block()
        } finally {
            if (active === owner) active = null
        }
    }
}

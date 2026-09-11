package com.lalilu.lmedia.source.sandbox

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.atomicMove
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.resolve
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Local sandbox files only, serialized by the source's operation mutex.
 * A sibling staging file keeps replacement on the same filesystem. This prevents
 * partial JSON replacement, but does not promise fsync/power-loss durability.
 */
internal class SandboxAtomicFileWriter(
    private val write: suspend (PlatformFile, String) -> Unit = { file, text -> file.writeString(text) },
    private val replace: suspend (PlatformFile, PlatformFile) -> Unit = { from, to -> from.atomicMove(to) },
) {
    suspend fun save(destination: PlatformFile, text: String, onCommitted: () -> Unit = {}) {
        val staging = requireNotNull(destination.parent()).resolve(destination.name + ".tmp")
        var failure: Throwable? = null
        try {
            currentCoroutineContext().ensureActive()
            write(staging, text)
            currentCoroutineContext().ensureActive()
            // FileKit moves on IO; cancellation on the return dispatcher must not
            // leave disk committed while the in-memory index still describes old data.
            withContext(NonCancellable) {
                replace(staging, destination)
                onCommitted()
            }
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            withContext(NonCancellable) {
                try {
                    if (staging.exists()) staging.delete()
                } catch (cleanup: Throwable) {
                    if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
                }
            }
        }
    }
}

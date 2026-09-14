package com.lalilu.lmedia.source.sandbox

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.atomicMove
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.exists
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Keeps the only copy recoverable until every downstream participant has acknowledged removal. */
internal suspend fun stagedSandboxDeletion(
    original: PlatformFile,
    quarantine: PlatformFile,
    removeAndConfirm: suspend () -> Unit,
    restoreAndConfirm: suspend () -> Unit,
) {
    check(!quarantine.exists()) { "Deletion recovery file already exists" }
    var staged = false
    var publicationStarted = false
    try {
        if (original.exists()) {
            original.atomicMove(quarantine)
            staged = true
        }
        publicationStarted = true
        removeAndConfirm()
        if (staged) quarantine.delete()
    } catch (failure: Throwable) {
        withContext(NonCancellable) {
            try {
                if (staged) {
                    check(!original.exists()) { "Cannot restore deletion over an existing file" }
                    quarantine.atomicMove(original)
                }
                if (publicationStarted) restoreAndConfirm()
            } catch (rollbackFailure: Throwable) {
                failure.addSuppressed(rollbackFailure)
            }
        }
        throw failure
    }
}

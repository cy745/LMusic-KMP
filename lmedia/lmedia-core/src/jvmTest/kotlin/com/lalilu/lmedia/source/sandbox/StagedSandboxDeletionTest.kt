package com.lalilu.lmedia.source.sandbox

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StagedSandboxDeletionTest {
    private fun fixture(block: (java.io.File, java.io.File) -> Unit) {
        val directory = Files.createTempDirectory("lmusic-delete-test-").toFile()
        try {
            val original = directory.resolve("song.mp3").apply { writeText("original audio bytes") }
            block(original, directory.resolve("quarantine"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun fileRemainsRecoverableUntilConfirmation() = fixture { original, quarantine -> runBlocking {
        stagedSandboxDeletion(PlatformFile(original), PlatformFile(quarantine), {
            assertFalse(original.exists())
            assertEquals("original audio bytes", quarantine.readText())
        }, { error("Unexpected rollback") })
        assertFalse(original.exists())
        assertFalse(quarantine.exists())
    } }

    @Test fun downstreamFailureRestoresBytesBeforeRestoringSnapshot() = fixture { original, quarantine -> runBlocking {
        var restored = false
        assertFailsWith<IllegalStateException> {
            stagedSandboxDeletion(PlatformFile(original), PlatformFile(quarantine), {
                error("Database commit failed")
            }, {
                assertEquals("original audio bytes", original.readText())
                restored = true
            })
        }
        assertTrue(restored)
        assertFalse(quarantine.exists())
    } }

    @Test fun cancellationStillRestoresFileAndSnapshot() = fixture { original, quarantine -> runBlocking {
        val started = CompletableDeferred<Unit>()
        var restored = false
        val job = launch {
            stagedSandboxDeletion(PlatformFile(original), PlatformFile(quarantine), {
                started.complete(Unit)
                awaitCancellation()
            }, { restored = true })
        }
        started.await()
        job.cancelAndJoin()
        assertTrue(restored)
        assertEquals("original audio bytes", original.readText())
        assertFalse(quarantine.exists())
    } }

    @Test fun rollbackConflictPreservesBothCopiesAndReportsFailure() = fixture { original, quarantine -> runBlocking {
        val failure = assertFailsWith<IllegalStateException> {
            stagedSandboxDeletion(PlatformFile(original), PlatformFile(quarantine), {
                original.writeText("new external file")
                error("Player update failed")
            }, { error("Must not publish a false restoration") })
        }
        assertEquals("new external file", original.readText())
        assertEquals("original audio bytes", quarantine.readText())
        assertTrue(failure.suppressedExceptions.isNotEmpty())
    } }

    @Test fun preexistingRecoveryFileIsNeverOverwritten() = fixture { original, quarantine -> runBlocking {
        quarantine.writeText("another deletion")
        assertFailsWith<IllegalStateException> {
            stagedSandboxDeletion(PlatformFile(original), PlatformFile(quarantine), {}, {})
        }
        assertEquals("original audio bytes", original.readText())
        assertEquals("another deletion", quarantine.readText())
    } }
}

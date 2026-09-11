package com.lalilu.lmedia.source.sandbox

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.atomicMove
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.*

class SandboxAtomicFileWriterTest {
    private fun fixture(block: suspend (File) -> Unit) = runBlocking {
        val directory = Files.createTempDirectory("lmusic-atomic-index-").toFile()
        try { block(directory.resolve("index.json").apply { writeText("old") }) }
        finally { directory.deleteRecursively() }
    }

    @Test fun replacesExistingFileBeforePublishingMemory() = fixture { target ->
        var memory = "old"
        SandboxAtomicFileWriter().save(PlatformFile(target), "new") {
            assertEquals("new", target.readText())
            memory = "new"
        }
        assertEquals("new", memory)
        assertFalse(File(target.path + ".tmp").exists())
    }

    @Test fun partialWriteFailureLeavesPreviousIndexIntact() = fixture { target ->
        val writer = SandboxAtomicFileWriter(write = { file, _ ->
            file.writeString("partial")
            throw IOException("disk full")
        })
        var published = false
        assertFailsWith<IOException> { writer.save(PlatformFile(target), "new") { published = true } }
        assertEquals("old", target.readText())
        assertFalse(published)
        assertFalse(File(target.path + ".tmp").exists())
    }

    @Test fun replacementFailureDoesNotDeleteOldIndex() = fixture { target ->
        val writer = SandboxAtomicFileWriter(replace = { _, _ -> throw IOException("move rejected") })
        assertFailsWith<IOException> { writer.save(PlatformFile(target), "new") { fail("Not committed") } }
        assertEquals("old", target.readText())
        assertFalse(File(target.path + ".tmp").exists())
    }

    @Test fun cancellationWhileWritingPreservesOldIndexAndCleansStaging() = fixture { target ->
        coroutineScope {
            val written = CompletableDeferred<Unit>()
            val writer = SandboxAtomicFileWriter(write = { file, text ->
                file.writeString(text)
                written.complete(Unit)
                awaitCancellation()
            })
            val job = launch { writer.save(PlatformFile(target), "new") { fail("Not committed") } }
            written.await()
            job.cancelAndJoin()
            assertEquals("old", target.readText())
            assertFalse(File(target.path + ".tmp").exists())
        }
    }

    @Test fun cancellationDuringReplacementStillPublishesCommittedIndex() = fixture { target ->
        coroutineScope {
            val replacing = CompletableDeferred<Unit>()
            val proceed = CompletableDeferred<Unit>()
            var memory = "old"
            val writer = SandboxAtomicFileWriter(replace = { from, to ->
                replacing.complete(Unit)
                proceed.await()
                from.atomicMove(to)
            })
            val job = launch { writer.save(PlatformFile(target), "new") { memory = "new" } }
            replacing.await()
            job.cancel()
            proceed.complete(Unit)
            job.join()
            assertTrue(job.isCancelled)
            assertEquals("new", target.readText())
            assertEquals("new", memory)
            assertFalse(File(target.path + ".tmp").exists())
        }
    }

    @Test fun leftoverStagingIsNotPromotedAndCanBeOverwritten() = fixture { target ->
        File(target.path + ".tmp").writeText("interrupted incomplete JSON")
        assertEquals("old", target.readText())
        SandboxAtomicFileWriter().save(PlatformFile(target), "next")
        assertEquals("next", target.readText())
        assertFalse(File(target.path + ".tmp").exists())
    }
}

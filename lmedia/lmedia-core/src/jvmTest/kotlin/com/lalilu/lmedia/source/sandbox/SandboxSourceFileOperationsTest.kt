package com.lalilu.lmedia.source.sandbox

import com.lalilu.lmedia.domain.model.Metadata
import com.lalilu.lmedia.domain.source.SnapshotState
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.*

/** Real files/index/snapshots; only native tag decoding is substituted. */
class SandboxSourceFileOperationsTest {
    private class Source(root: File, cache: File) : AbstractSandboxMediaSource(PlatformFile(root), PlatformFile(cache)) {
        var metadataReader: suspend (PlatformFile) -> Metadata? = { Metadata(title = it.name) }
        override fun buildPlaybackUrl(file: PlatformFile) = "unused"
        override suspend fun readAudioMetadata(file: PlatformFile) = metadataReader(file)
    }

    private fun fixture(block: suspend (File, File, Source) -> Unit) = runBlocking {
        val directory = Files.createTempDirectory("lmusic-sandbox-source-").toFile()
        val root = directory.resolve("sandbox").apply { mkdirs() }
        val cache = directory.resolve("cache")
        val source = Source(root, cache)
        try { block(root, cache, source) } finally {
            source.deactivate()
            directory.deleteRecursively()
        }
    }

    private fun audio(file: File) = file.apply { writeBytes("ID3".toByteArray() + ByteArray(100)) }

    @Test fun corruptIndexRejectsImportWithoutOverwritingIndexOrCopyingAudio() = fixture { root, cache, source ->
        val index = root.resolve(".lmusic-sandbox-index.json").apply { writeText("{\"entries\":") }
        val external = audio(root.parentFile.resolve("external.mp3"))
        val failure = assertFailsWith<IllegalStateException> {
            source.import(PlatformFile(external), emptyList())
        }
        assertTrue(failure.message.orEmpty().contains("index"))
        assertEquals("{\"entries\":", index.readText())
        assertTrue(external.exists())
        assertTrue(root.resolve("Imported").listFiles()!!.isEmpty())
        assertTrue(cache.listFiles()!!.isEmpty())
        assertNull(source.snapshot.value)
    }

    @Test fun failedIndexLoadCanRetryWithoutLosingRenamedSongIdentity() = fixture { root, cache, source ->
        val external = audio(root.parentFile.resolve("external.mp3"))
        val imported = source.import(PlatformFile(external), emptyList()).audio
        source.rename(imported, "renamed") {}
        val index = root.resolve(".lmusic-sandbox-index.json")
        val validIndex = index.readText()
        source.deactivate()
        index.writeText("truncated")
        val restarted = Source(root, cache)
        try {
            restarted.init()
            withTimeout(5_000) { restarted.state.first { it is SnapshotState.Error } }
            assertNull(restarted.snapshot.value)
            assertFalse(restarted.contentState.value.isReady)
            assertEquals("truncated", index.readText())
            assertTrue(root.resolve("Imported/renamed.mp3").exists())
            // Simulate restoration of a known-good index; failed loading must not cache an empty index.
            index.writeText(validIndex)
            restarted.refresh()
            val restored = withTimeout(5_000) { restarted.snapshot.filterNotNull().first() }
            assertEquals(imported.id, restored.audios.single().id)
            assertTrue(restored.audios.single().extra!!.getValue("path").endsWith("renamed.mp3"))
        } finally { restarted.deactivate() }
    }

    @Test fun renameRetainsIdentityAndWaitsForDownstreamConfirmation() = fixture { root, _, source ->
        val external = audio(root.parentFile.resolve("external.mp3"))
        val original = source.import(PlatformFile(external), emptyList()).audio
        var confirmed = false
        val renamed = source.rename(original, "renamed") { snapshot ->
            assertTrue(source.contentState.value.isReady)
            assertEquals(original.id, snapshot.audios.single().id)
            assertTrue(root.resolve("Imported/renamed.mp3").exists())
            assertFalse(root.resolve("Imported/external.mp3").exists())
            confirmed = true
        }
        assertTrue(confirmed)
        assertEquals(original.id, renamed.id)
    }

    @Test fun downstreamRenameFailureRestoresPathIndexAndSnapshot() = fixture { root, _, source ->
        val external = audio(root.parentFile.resolve("external.mp3"))
        val original = source.import(PlatformFile(external), emptyList()).audio
        val previousIndex = root.resolve(".lmusic-sandbox-index.json").readText()
        val paths = mutableListOf<String>()
        assertFailsWith<IllegalStateException> {
            source.rename(original, "renamed") { snapshot ->
                val path = snapshot.audios.single().extra!!.getValue("path")
                paths += File(path).name
                if (paths.size == 1) error("Database failure")
            }
        }
        assertEquals(listOf("renamed.mp3", "external.mp3"), paths)
        assertEquals(previousIndex, root.resolve(".lmusic-sandbox-index.json").readText())
        assertEquals(original.id, source.snapshot.value!!.audios.single().id)
        assertTrue(root.resolve("Imported/external.mp3").exists())
        assertFalse(root.resolve("Imported/renamed.mp3").exists())
    }

    @Test fun renameDoesNotOverwriteExistingDestination() = fixture { root, _, source ->
        val external = audio(root.parentFile.resolve("external.mp3"))
        val original = source.import(PlatformFile(external), emptyList()).audio
        val occupied = root.resolve("Imported/occupied.mp3").apply { writeText("keep me") }
        assertFailsWith<IllegalArgumentException> { source.rename(original, "occupied") {} }
        assertEquals("keep me", occupied.readText())
        assertTrue(root.resolve("Imported/external.mp3").exists())
    }

    @Test fun deletionFailurePublishesRestoredSnapshotAndKeepsStableId() = fixture { root, _, source ->
        val file = audio(root.resolve("original.mp3"))
        source.init()
        val initial = withTimeout(5_000) { source.snapshot.filterNotNull().first() }
        val song = initial.audios.single()
        val observations = mutableListOf<Boolean>()
        assertFailsWith<IllegalStateException> {
            source.delete(song) { snapshot ->
                val present = snapshot.audios.any { it.id == song.id }
                observations += present
                if (!present) error("Simulated database failure")
            }
        }
        assertEquals(listOf(false, true), observations)
        assertTrue(file.exists())
        assertEquals(song.id, source.snapshot.value!!.audios.single().id)
        assertTrue(root.resolve(".pending-deletions").listFiles()!!.isEmpty())
    }

    @Test fun successfulDeletionRemovesFileAndJournalOnlyAfterConfirmation() = fixture { root, _, source ->
        val file = audio(root.resolve("original.mp3"))
        source.init()
        val song = withTimeout(5_000) { source.snapshot.filterNotNull().first() }.audios.single()
        source.delete(song) { snapshot ->
            assertTrue(source.contentState.value.isReady)
            assertTrue(snapshot.audios.isEmpty())
            assertFalse(file.exists())
            assertEquals(2, root.resolve(".pending-deletions").listFiles()!!.size)
        }
        assertFalse(file.exists())
        assertTrue(root.resolve(".pending-deletions").listFiles()!!.isEmpty())
    }

    @Test fun interruptedDeletionIsRestoredBeforeFirstScan() = fixture { root, _, source ->
        val recovery = root.resolve(".pending-deletions").apply { mkdirs() }
        audio(recovery.resolve("delete-test"))
        recovery.resolve("delete-test.json").writeText("""{"relativePath":"/original.mp3","entry":null}""")
        source.init()
        val snapshot = withTimeout(5_000) { source.snapshot.filterNotNull().first() }
        assertEquals(1, snapshot.audios.size)
        assertTrue(root.resolve("original.mp3").exists())
        assertTrue(recovery.listFiles()!!.isEmpty())
    }

    @Test fun duplicateImportLeavesSourceReadyRatherThanLoading() = fixture { root, cache, source ->
        val external = audio(root.parentFile.resolve("external.mp3"))
        val first = source.import(PlatformFile(external), emptyList()).audio
        val again = source.import(PlatformFile(external), listOf(first))
        assertIs<SandboxImportResult.Existing>(again)
        assertFalse(source.state.value is SnapshotState.Loading)
        assertEquals(1, source.snapshot.value!!.audios.size)
        assertTrue(cache.listFiles()!!.isEmpty())
    }

    @Test fun scanFailureAfterMoveRestoresIndexAndRemovesOnlyImportedCopy() = fixture { root, cache, source ->
        val external = audio(root.parentFile.resolve("external.mp3"))
        val first = source.import(PlatformFile(external), emptyList()).audio
        val previousIndex = root.resolve(".lmusic-sandbox-index.json").readText()
        val previousSnapshot = source.snapshot.value
        val second = audio(root.parentFile.resolve("second.mp3")).apply { appendBytes(byteArrayOf(1)) }
        source.metadataReader = { if (it.name == "second.mp3") error("Injected scan failure") else Metadata(title = it.name) }
        assertFailsWith<IllegalStateException> { source.import(PlatformFile(second), listOf(first)) }
        assertEquals(previousIndex, root.resolve(".lmusic-sandbox-index.json").readText())
        assertEquals(previousSnapshot, source.snapshot.value)
        assertEquals(listOf("external.mp3"), root.resolve("Imported").list()!!.toList())
        assertTrue(second.exists())
        assertTrue(cache.listFiles()!!.isEmpty())
    }

    @Test fun parserRejectingMovedFileDoesNotPublishFalseSuccess() = fixture { root, cache, source ->
        val external = audio(root.parentFile.resolve("external.mp3"))
        source.metadataReader = { if (it.name == "external.mp3") null else Metadata() }
        assertFailsWith<UnsupportedExternalAudioException> { source.import(PlatformFile(external), emptyList()) }
        assertNull(source.snapshot.value)
        assertTrue(root.resolve("Imported").listFiles()!!.isEmpty())
        assertTrue(cache.listFiles()!!.isEmpty())
        assertTrue(external.exists())
    }

    @Test fun cancellationAfterMoveAlsoRemovesCopyAndRestoresIndex() = fixture { root, cache, source ->
        val external = audio(root.parentFile.resolve("external.mp3"))
        source.metadataReader = {
            if (it.name == "external.mp3") throw kotlinx.coroutines.CancellationException("cancel scan")
            Metadata()
        }
        assertFailsWith<kotlinx.coroutines.CancellationException> { source.import(PlatformFile(external), emptyList()) }
        assertNull(source.snapshot.value)
        assertTrue(root.resolve("Imported").listFiles()!!.isEmpty())
        assertTrue(cache.listFiles()!!.isEmpty())
        assertTrue(external.exists())
    }
}

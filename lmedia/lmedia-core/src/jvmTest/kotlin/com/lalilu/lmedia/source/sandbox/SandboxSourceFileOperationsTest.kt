package com.lalilu.lmedia.source.sandbox

import com.lalilu.lmedia.domain.model.Metadata
import com.lalilu.lmedia.domain.source.SnapshotState
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    /**
     * 归属判断不能依赖 '/'：Windows 的路径分隔符是 '\'，旧的 `path.startsWith("$root/")`
     * 会把所有嵌套路径误判成越界，导致导入后的曲目无法重命名/删除。
     */
    @Test fun nestedSandboxPathIsOwnedWhateverThePlatformSeparator() = fixture { root, _, source ->
        val external = audio(root.parentFile.resolve("external.mp3"))
        val original = source.import(PlatformFile(external), emptyList()).audio
        val stored = original.extra!!.getValue("path")
        assertTrue(stored.startsWith(root.path), "沙箱内路径应以根目录开头：$stored")
        assertTrue(stored.length > root.path.length, "导入后应为嵌套路径，而不是根目录本身：$stored")
        assertTrue(stored.contains(File.separatorChar), "嵌套路径应使用平台分隔符 ${File.separatorChar}：$stored")

        // rename 会走 requireOwnedPath 的嵌套分支；分隔符判断错误时这里会抛
        // "Song path is outside the sandbox"（旧实现在 Windows 上必失败）。
        val renamed = source.rename(original, "separator-check") {}
        assertEquals(original.id, renamed.id)
        assertTrue(root.resolve("Imported/separator-check.mp3").exists())
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

    /**
     * 并发导入必须按源串行：每个文件各自落名、索引一条不少、缓存区不留半成品。
     * 串行化一旦丢失（索引"读-改-写"交错），文件系统看起来仍然正常，只有索引条目数会少，
     * 所以这里直接断言索引里的条目数等于导入数。
     */
    @Test fun concurrentImportsSerializeWithoutLosingIndexEntriesOrLeavingHalfFiles() = fixture { root, cache, source ->
        val files = (1..4).map { index ->
            audio(root.parentFile.resolve("concurrent-$index.mp3"))
                .apply { appendBytes(byteArrayOf(index.toByte())) }
        }
        val imported = coroutineScope {
            files.map { file -> async { source.import(PlatformFile(file), emptyList()).audio } }
        }.awaitAll()

        assertEquals(4, imported.map { it.id }.toSet().size, "四份不同内容必须得到四个不同身份")
        assertEquals(4, root.resolve("Imported").listFiles()!!.size)
        assertTrue(cache.listFiles()!!.isEmpty(), "导入结束后缓存区不能残留临时文件")
        val index = root.resolve(".lmusic-sandbox-index.json").readText()
        assertEquals(4, Regex("\"audioId\"").findAll(index).count(), "索引条目数必须等于导入数：$index")
    }

    /**
     * 不同目录下的同名文件：第二个必须让位到不冲突的名字，既不覆盖第一个也不共用身份。
     * `allocateDestination` 的 " (1)" 分支此前没有任何用例覆盖。
     */
    @Test fun sameNamedImportsFromDifferentFoldersAllocateNonConflictingNames() = fixture { root, _, source ->
        val firstDirectory = root.parentFile.resolve("first").apply { mkdirs() }
        val secondDirectory = root.parentFile.resolve("second").apply { mkdirs() }
        val first = audio(firstDirectory.resolve("external.mp3"))
        val second = audio(secondDirectory.resolve("external.mp3")).apply { appendBytes(byteArrayOf(7)) }

        val firstSong = source.import(PlatformFile(first), emptyList()).audio
        val secondSong = source.import(PlatformFile(second), emptyList()).audio

        assertNotEquals(firstSong.id, secondSong.id)
        assertEquals(listOf("external (1).mp3", "external.mp3"), root.resolve("Imported").list()!!.sorted())
        assertEquals(
            setOf("external (1).mp3", "external.mp3"),
            source.snapshot.value!!.audios.map { File(it.extra!!.getValue("path")).name }.toSet(),
        )
        assertTrue(first.exists() && second.exists(), "外部原件既不能被移动也不能被改写")
    }

    /**
     * 扩展名像音频、内容不是音频：必须在真正落盘之前被 MagicNumber 拒绝，临时文件清理干净。
     * 已有的"解析失败"用例只模拟了 Taglib 返回 null，走不到这条分支。
     */
    @Test fun contentThatIsNotAudioIsRejectedByMagicNumber() = fixture { root, cache, source ->
        val fake = root.parentFile.resolve("fake.mp3")
            .apply { writeBytes("this is definitely not audio".toByteArray()) }

        assertFailsWith<UnsupportedExternalAudioException> { source.import(PlatformFile(fake), emptyList()) }

        assertNull(source.snapshot.value)
        assertTrue(root.resolve("Imported").listFiles()!!.isEmpty())
        assertTrue(cache.listFiles()!!.isEmpty())
        assertTrue(fake.exists())
    }
}

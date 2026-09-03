package com.lalilu.lmedia.source.sandbox

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.common.ext.md5
import com.lalilu.lmedia.MagicNumber
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.Metadata
import com.lalilu.lmedia.domain.model.toAudioExtra
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.MediaDataSource
import com.lalilu.lmedia.domain.source.MediaSourceStateStore
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.SnapshotState
import com.lalilu.lmedia.source.external.ExternalMediaMatch
import com.lalilu.lmedia.source.external.ExternalMediaMatchBasis
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.atomicMove
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.isDirectory
import io.github.vinceglb.filekit.lastModified
import io.github.vinceglb.filekit.list
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readString
import io.github.vinceglb.filekit.resolve
import io.github.vinceglb.filekit.sink
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.source
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.random.Random
import kotlin.time.ExperimentalTime

abstract class AbstractSandboxMediaSource(
    private val rootDirectory: PlatformFile,
    private val workingDirectory: PlatformFile,
) : SandboxMediaSource, MediaDataSource {
    final override val name: String = SOURCE_NAME
    final override val dataSource: MediaDataSource = this

    private val logger = Logger.withTag(SOURCE_NAME)
    private val scope = CoroutineScope(Dispatchers.io + SupervisorJob())
    private val stateStore = MediaSourceStateStore()
    private val operationMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val importedDirectory = rootDirectory.resolve(IMPORTED_DIRECTORY_NAME)
    private val indexFile = rootDirectory.resolve(INDEX_FILE_NAME)
    private var index: SandboxIndex? = null
    private var loadingJob: Job? = null

    final override val state: StateFlow<SnapshotState> = stateStore.state
    final override val snapshot: StateFlow<Snapshot?> = stateStore.snapshot
    final override val contentState = stateStore.contentState

    protected abstract fun buildPlaybackUrl(file: PlatformFile): String

    final override fun init() = refresh(preserveReady = false)

    final override fun refresh() = refresh(preserveReady = true)

    private fun refresh(preserveReady: Boolean) {
        loadingJob?.cancel()
        loadingJob = scope.launch {
            operationMutex.withLock {
                publishScan(preserveReady = preserveReady)
            }
        }
    }

    final override fun cancel() {
        loadingJob?.cancel()
        stateStore.content.unavailable("Cancelled", preserveReady = true)
    }

    fun reset() {
        loadingJob?.cancel()
        stateStore.content.unavailable("Not initialized")
        loadingJob = scope.launch { stateStore.reset() }
    }

    final override suspend fun matchExternalMedia(
        file: PlatformFile,
        candidates: List<LAudio>,
    ): ExternalMediaMatch? = operationMutex.withLock {
        findOwnedPathMatch(file, candidates)?.let {
            return@withLock ExternalMediaMatch(it, ExternalMediaMatchBasis.SandboxPath)
        }

        val digest = digest(file)
        findDigestMatch(digest, candidates)?.let {
            ExternalMediaMatch(it, ExternalMediaMatchBasis.ContentDigest)
        }
    }

    final override suspend fun import(
        file: PlatformFile,
        candidates: List<LAudio>,
    ): SandboxImportResult = operationMutex.withLock {
        findOwnedPathMatch(file, candidates)?.let { return@withLock SandboxImportResult.Existing(it) }

        loadingJob?.cancelAndJoin()
        ensureDirectories()
        val taskId = stateStore.begin(message = "Importing ${file.name}")
        stateStore.content.preparing()
        var temporaryFile: PlatformFile? = null

        try {
            ensureIndexLoaded()
            temporaryFile = allocateTemporaryFile(file)
            val digest = copyAndDigest(file, temporaryFile)
            val magicNumber = MagicNumber.match(
                ext = file.extension,
                source = temporaryFile.source().buffered(),
            ) ?: throw UnsupportedExternalAudioException("Unsupported audio file: ${file.name}")

            val metadata = Taglib.readMetadata(temporaryFile.path)
                ?: throw UnsupportedExternalAudioException("Unable to parse audio metadata: ${file.name}")

            findDigestMatch(digest, candidates)?.let { existing ->
                temporaryFile.delete(mustExist = false)
                return@withLock SandboxImportResult.Existing(existing)
            }

            val destination = allocateDestination(file.name, magicNumber)
            temporaryFile.atomicMove(destination)
            temporaryFile = null

            updateIndex(destination, digest)
            val snapshot = stateStore.succeed(taskId, scan())
                ?: error("Sandbox scan was superseded")
            stateStore.content.ready()
            val imported = snapshot.audios.firstOrNull { it.id == audioId(destination) }
                ?: buildAudio(destination, metadata)
            SandboxImportResult.Imported(imported, snapshot.revision)
        } catch (cancelled: CancellationException) {
            temporaryFile?.delete(mustExist = false)
            stateStore.cancel(taskId)
            throw cancelled
        } catch (throwable: Throwable) {
            temporaryFile?.delete(mustExist = false)
            logger.e(throwable) { "Import failed: ${file.name}" }
            stateStore.fail(taskId, throwable.message ?: "Unknown import error")
            stateStore.content.unavailable(
                throwable.message ?: "Unknown import error",
                preserveReady = true,
            )
            throw throwable
        }
    }

    final override suspend fun rename(audio: LAudio, newBaseName: String): LAudio =
        operationMutex.withLock {
            loadingJob?.cancelAndJoin()
            ensureDirectories()
            ensureIndexLoaded()

            val source = requireOwnedFile(audio)
            require(source.exists()) { "Sandbox file no longer exists" }
            val parent = source.parent() ?: error("Sandbox file has no parent directory")
            val destination = parent.resolve(renamedFileName(source.name, newBaseName))
            if (destination.path == source.path) return@withLock audio
            require(!destination.exists()) { "A file with this name already exists" }

            val taskId = stateStore.begin(message = "Renaming ${source.name}")
            stateStore.content.preparing(preserveReady = true)
            val previousIndex = indexOrEmpty()
            var moved = false
            try {
                val oldRelativePath = relativePath(source)
                val oldEntry = previousIndex.entries[oldRelativePath]
                    ?.takeIf { it.size == source.size() && it.modifiedAt == modifiedAt(source) }
                val fileDigest = oldEntry?.let { FileDigest(it.sha256, it.size) } ?: digest(source)
                val stableAudioId = oldEntry?.audioId ?: audio.id

                source.atomicMove(destination)
                moved = true

                val newRelativePath = relativePath(destination)
                val entries = previousIndex.entries.toMutableMap().apply {
                    remove(oldRelativePath)
                    put(
                        newRelativePath,
                        SandboxIndexEntry(
                            audioId = stableAudioId,
                            relativePath = newRelativePath,
                            sha256 = fileDigest.sha256,
                            size = fileDigest.size,
                            modifiedAt = modifiedAt(destination),
                        )
                    )
                }
                saveIndex(SandboxIndex(entries))

                val snapshot = stateStore.succeed(taskId, scan())
                    ?: error("Sandbox rename was superseded")
                stateStore.content.ready()
                snapshot.audios.firstOrNull { it.id == stableAudioId }
                    ?: error("Renamed audio is missing from sandbox snapshot")
            } catch (cancelled: CancellationException) {
                if (moved) rollbackRename(destination, source, previousIndex)
                stateStore.cancel(taskId)
                throw cancelled
            } catch (throwable: Throwable) {
                if (moved) rollbackRename(destination, source, previousIndex)
                logger.e(throwable) { "Rename failed: ${source.name}" }
                stateStore.fail(taskId, throwable.message ?: "Rename failed")
                stateStore.content.unavailable(
                    throwable.message ?: "Rename failed",
                    preserveReady = true,
                )
                throw throwable
            }
        }

    private suspend fun rollbackRename(
        destination: PlatformFile,
        source: PlatformFile,
        previousIndex: SandboxIndex,
    ) = withContext(NonCancellable) {
        runCatching { destination.atomicMove(source) }
            .onFailure { logger.e(it) { "Failed to roll back sandbox file rename" } }
        runCatching { saveIndex(previousIndex) }
            .onFailure { logger.e(it) { "Failed to roll back sandbox index" } }
    }

    final override suspend fun delete(audio: LAudio) = operationMutex.withLock {
        loadingJob?.cancelAndJoin()
        ensureDirectories()
        ensureIndexLoaded()

        val file = requireOwnedFile(audio)
        val taskId = stateStore.begin(message = "Deleting ${file.name}")
        stateStore.content.preparing(preserveReady = true)
        try {
            file.delete(mustExist = false)
            val entries = indexOrEmpty().entries.toMutableMap().apply {
                remove(relativePath(file))
            }
            saveIndex(SandboxIndex(entries))
            stateStore.succeed(taskId, scan())
                ?: error("Sandbox delete was superseded")
            stateStore.content.ready()
        } catch (cancelled: CancellationException) {
            stateStore.cancel(taskId)
            throw cancelled
        } catch (throwable: Throwable) {
            logger.e(throwable) { "Delete failed: ${file.name}" }
            stateStore.fail(taskId, throwable.message ?: "Delete failed")
            stateStore.content.unavailable(
                throwable.message ?: "Delete failed",
                preserveReady = true,
            )
            throw throwable
        }
    }

    final override suspend fun getLyric(song: LAudio): String? {
        val path = requireOwnedPath(song)
        return Taglib.getLyric(path)
    }

    final override suspend fun getPicture(song: LAudio): MediaData? {
        val path = requireOwnedPath(song)
        return Taglib.getPicture(path)?.let(MediaData::Bytes)
    }

    final override suspend fun getMedia(song: LAudio): MediaData? {
        if (song.mediaSourceName != name) return null
        val path = requireOwnedPath(song)
        return MediaData.Url(buildPlaybackUrl(PlatformFile(path)))
    }

    private suspend fun publishScan(preserveReady: Boolean) {
        val taskId = stateStore.begin()
        stateStore.content.preparing(preserveReady)
        try {
            if (stateStore.succeed(taskId, scan()) != null) {
                stateStore.content.ready()
            }
        } catch (cancelled: CancellationException) {
            if (stateStore.cancel(taskId)) {
                stateStore.content.unavailable("Cancelled", preserveReady = true)
            }
            throw cancelled
        } catch (throwable: Throwable) {
            logger.e(throwable) { "Scan failed" }
            if (stateStore.fail(taskId, throwable.message ?: "Unknown error")) {
                stateStore.content.unavailable(
                    throwable.message ?: "Unknown error",
                    preserveReady = preserveReady,
                )
            }
        }
    }

    private suspend fun scan(): List<LAudio> = withContext(Dispatchers.io) {
        ensureDirectories()
        ensureIndexLoaded()
        val files = scanFiles(rootDirectory)
        files.mapNotNull { file ->
            currentCoroutineContext().ensureActive()
            val metadata = Taglib.readMetadata(file.path) ?: return@mapNotNull null
            buildAudio(file, metadata)
        }
    }

    private fun scanFiles(
        directory: PlatformFile,
        result: MutableList<PlatformFile> = mutableListOf(),
    ): List<PlatformFile> {
        directory.list().forEach { file ->
            if (file.path == indexFile.path) return@forEach
            if (file.isDirectory()) {
                scanFiles(file, result)
            } else if (file.size() >= MIN_AUDIO_SIZE && runCatching {
                    MagicNumber.match(file.extension, file.source().buffered()) != null
                }.getOrDefault(false)
            ) {
                result += file
            }
        }
        return result
    }

    private fun buildAudio(file: PlatformFile, metadata: Metadata): LAudio {
        val entry = indexOrEmpty().entries[relativePath(file)]
            ?.takeIf { it.size == file.size() && it.modifiedAt == modifiedAt(file) }
        val sourceExtra = buildMap {
            put(EXTRA_PATH, file.path)
            put(EXTRA_FILE_SIZE, file.size().toString())
            entry?.let {
                put(EXTRA_DIGEST_ALGORITHM, DIGEST_ALGORITHM)
                put(EXTRA_CONTENT_DIGEST, it.sha256)
            }
        }
        return LAudio(
            id = entry?.audioId ?: audioId(file),
            title = metadata.title ?: "Unknown",
            subtitle = metadata.artist ?: "Unknown",
            mediaSourceName = name,
            extra = metadata.toAudioExtra(sourceExtra),
        )
    }

    private fun findOwnedPathMatch(file: PlatformFile, candidates: List<LAudio>): LAudio? {
        if (!isInsideRoot(file)) return null
        val id = indexOrEmpty().entries[relativePath(file)]?.audioId ?: audioId(file)
        return candidates.firstOrNull { it.mediaSourceName == name && it.id == id }
            ?: snapshot.value?.audios?.firstOrNull { it.id == id }
    }

    private suspend fun findDigestMatch(
        digest: FileDigest,
        candidates: List<LAudio>,
    ): LAudio? {
        ensureIndexLoaded()
        val sandboxCandidates = (candidates + snapshot.value.orEmpty().audios)
            .asSequence()
            .filter { it.mediaSourceName == name }
            .distinctBy { it.id }
            .toList()
        val mutableEntries = indexOrEmpty().entries.toMutableMap()

        mutableEntries.values.firstOrNull {
            it.sha256 == digest.sha256 && it.size == digest.size
        }?.let { entry ->
            sandboxCandidates.firstOrNull { it.id == entry.audioId }?.let { return it }
        }

        var indexChanged = false
        for (candidate in sandboxCandidates) {
            val path = candidate.extra?.get(EXTRA_PATH) ?: continue
            val candidateFile = PlatformFile(path)
            if (!candidateFile.exists()) continue
            val relativePath = relativePath(candidateFile)
            val cached = mutableEntries[relativePath]
            val currentSize = candidateFile.size()
            val currentModifiedAt = modifiedAt(candidateFile)
            val entry = if (cached != null &&
                cached.size == currentSize &&
                cached.modifiedAt == currentModifiedAt
            ) {
                cached
            } else {
                digest(candidateFile).let {
                    SandboxIndexEntry(
                        audioId = candidate.id,
                        relativePath = relativePath,
                        sha256 = it.sha256,
                        size = it.size,
                        modifiedAt = currentModifiedAt,
                    )
                }.also {
                    mutableEntries[relativePath] = it
                    indexChanged = true
                }
            }
            if (entry.sha256 == digest.sha256 && entry.size == digest.size) {
                if (indexChanged) saveIndex(SandboxIndex(mutableEntries))
                return candidate
            }
        }

        if (indexChanged) saveIndex(SandboxIndex(mutableEntries))
        return null
    }

    private suspend fun updateIndex(file: PlatformFile, digest: FileDigest) {
        ensureIndexLoaded()
        val relativePath = relativePath(file)
        val entries = indexOrEmpty().entries.toMutableMap()
        entries[relativePath] = SandboxIndexEntry(
            audioId = audioId(file),
            relativePath = relativePath,
            sha256 = digest.sha256,
            size = digest.size,
            modifiedAt = modifiedAt(file),
        )
        saveIndex(SandboxIndex(entries))
    }

    private suspend fun ensureIndexLoaded(): SandboxIndex {
        index?.let { return it }
        return if (indexFile.exists()) {
            runCatching { json.decodeFromString<SandboxIndex>(indexFile.readString()) }
                .onFailure { logger.w(it) { "Ignoring invalid sandbox index" } }
                .getOrDefault(SandboxIndex())
        } else {
            SandboxIndex()
        }.also { index = it }
    }

    private fun indexOrEmpty(): SandboxIndex = index ?: SandboxIndex()

    private suspend fun saveIndex(value: SandboxIndex) {
        indexFile.writeString(json.encodeToString(SandboxIndex.serializer(), value))
        index = value
    }

    private fun ensureDirectories() {
        rootDirectory.createDirectories()
        importedDirectory.createDirectories()
        workingDirectory.createDirectories()
    }

    private fun allocateTemporaryFile(source: PlatformFile): PlatformFile {
        val extension = source.extension.takeIf(String::isNotBlank)?.let { ".$it" }.orEmpty()
        while (true) {
            val candidate = workingDirectory.resolve("import-${randomToken()}$extension")
            if (!candidate.exists()) return candidate
        }
    }

    private fun allocateDestination(originalName: String, magicNumber: MagicNumber): PlatformFile {
        val safeName = sanitizeFileName(originalName)
        val sourceExtension = safeName.substringAfterLast('.', "").lowercase()
        val extension = sourceExtension.takeIf { it in magicNumber.ext } ?: magicNumber.ext.first()
        val baseName = safeName.substringBeforeLast('.', safeName)
            .ifBlank { "audio" }
            .take(MAX_BASE_NAME_LENGTH)
        var suffix = 0
        while (true) {
            val suffixText = if (suffix == 0) "" else " ($suffix)"
            val candidate = importedDirectory.resolve("$baseName$suffixText.$extension")
            if (!candidate.exists()) return candidate
            suffix += 1
        }
    }

    private suspend fun copyAndDigest(source: PlatformFile, destination: PlatformFile): FileDigest =
        withContext(Dispatchers.io) {
            val sha256 = SHA256()
            var total = 0L
            source.source().buffered().use { input ->
                destination.sink().buffered().use { output ->
                    val buffer = Buffer()
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.readAtMostTo(buffer, COPY_BUFFER_SIZE)
                        if (read == -1L) break
                        val bytes = buffer.readByteArray(read.toInt())
                        sha256.update(bytes)
                        output.write(bytes)
                        total += read
                    }
                }
            }
            FileDigest(sha256.digest().toHex(), total)
        }

    private suspend fun digest(file: PlatformFile): FileDigest = withContext(Dispatchers.io) {
        val sha256 = SHA256()
        var total = 0L
        file.source().buffered().use { input ->
            val buffer = Buffer()
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.readAtMostTo(buffer, COPY_BUFFER_SIZE)
                if (read == -1L) break
                val bytes = buffer.readByteArray(read.toInt())
                sha256.update(bytes)
                total += read
            }
        }
        FileDigest(sha256.digest().toHex(), total)
    }

    private fun audioId(file: PlatformFile): String = sandboxAudioId(
        rootPath = rootDirectory.path,
        filePath = file.path,
    )

    private fun relativePath(file: PlatformFile): String = sandboxRelativePath(
        rootPath = rootDirectory.path,
        filePath = file.path,
    )

    private fun isInsideRoot(file: PlatformFile): Boolean {
        val root = rootDirectory.path.trimEnd('/')
        val path = file.path
        return path == root || path.startsWith("$root/")
    }

    private fun requireOwnedPath(song: LAudio): String {
        require(song.mediaSourceName == name) { "Song does not belong to $name: ${song.id}" }
        val path = song.extra?.get(EXTRA_PATH)
            ?: throw IllegalArgumentException("Missing sandbox path: ${song.id}")
        require(isInsideRoot(PlatformFile(path))) { "Song path is outside the sandbox: ${song.id}" }
        return path
    }

    private fun requireOwnedFile(song: LAudio): PlatformFile = PlatformFile(requireOwnedPath(song))

    @OptIn(ExperimentalTime::class)
    private fun modifiedAt(file: PlatformFile): Long =
        runCatching { file.lastModified().toEpochMilliseconds() }.getOrDefault(0L)

    private fun randomToken(): String = Random.nextLong().toULong().toString(16)

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        byte.toUByte().toString(16).padStart(2, '0')
    }

    private data class FileDigest(val sha256: String, val size: Long)

    @Serializable
    private data class SandboxIndex(
        val entries: Map<String, SandboxIndexEntry> = emptyMap(),
    )

    @Serializable
    private data class SandboxIndexEntry(
        val audioId: String,
        val relativePath: String,
        val sha256: String,
        val size: Long,
        val modifiedAt: Long,
    )

    companion object {
        const val SOURCE_NAME = "SandboxFileSystemSource"
        const val EXTRA_PATH = SandboxMediaSource.EXTRA_PATH
        const val EXTRA_CONTENT_DIGEST = "content_digest"
        const val EXTRA_DIGEST_ALGORITHM = "digest_algorithm"
        const val EXTRA_FILE_SIZE = SandboxMediaSource.EXTRA_FILE_SIZE
        const val DIGEST_ALGORITHM = "sha256"

        private const val IMPORTED_DIRECTORY_NAME = "Imported"
        private const val INDEX_FILE_NAME = ".lmusic-sandbox-index.json"
        private const val MIN_AUDIO_SIZE = 10L
        private const val COPY_BUFFER_SIZE = 64L * 1024L
        private const val MAX_BASE_NAME_LENGTH = 100

        /** Keep the leading slash to remain compatible with the original iOS source IDs. */
        internal fun sandboxRelativePath(rootPath: String, filePath: String): String =
            filePath.substringAfter(rootPath)

        internal fun sandboxAudioId(rootPath: String, filePath: String): String =
            "${LAudio.ID_PREFIX}${sandboxRelativePath(rootPath, filePath).md5()}"

        internal fun sanitizeFileName(value: String): String = value
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[\\u0000-\\u001F/:*?\"<>|]"), "_")
            .trim()
            .trim('.')
            .ifBlank { "audio" }

        internal fun renamedFileName(originalFileName: String, requestedBaseName: String): String {
            require(requestedBaseName.isNotBlank()) { "File name cannot be blank" }
            val extension = originalFileName.substringAfterLast('.', "")
            val safeName = sanitizeFileName(requestedBaseName)
            val baseName = if (extension.isNotBlank()) {
                safeName.takeIf { it.endsWith(".$extension", ignoreCase = true) }
                    ?.dropLast(extension.length + 1)
                    ?: safeName
            } else {
                safeName
            }.trimEnd('.').ifBlank { "audio" }.take(MAX_BASE_NAME_LENGTH)
            return if (extension.isBlank()) baseName else "$baseName.$extension"
        }
    }
}

private fun Snapshot?.orEmpty(): Snapshot = this ?: Snapshot()

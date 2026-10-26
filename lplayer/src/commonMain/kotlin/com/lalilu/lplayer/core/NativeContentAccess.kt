package com.lalilu.lplayer.core

import com.lalilu.lmedia.content.*
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.lmedia.domain.model.mediaKey
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.repository.getAudioByPlaybackId
import com.lalilu.lmedia.domain.source.*
import com.lalilu.lplayer.LPlayerKV
import io.ktor.client.HttpClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/** Shared native-host content policy. Platform bridges only translate byte requests. */
class NativeContentAccess(
    parentScope: CoroutineScope,
    private val sources: PlatformMediaSource,
    private val audios: AudioRepository,
    private val http: HttpClient,
    catalogRoot: String,
    private val contentRoot: String,
    private val cacheEnabled: () -> Boolean = { LPlayerKV.contentCacheEnabled.value },
    quotaMiB: Flow<Long> = LPlayerKV.contentCacheQuotaMiB.flow().map { it.toLongOrNull() ?: 512 },
    private val restoreMetadata: suspend (List<LAudio>) -> Unit = {},
    private val io: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val known = MutableStateFlow<Map<MediaKey, LAudio>>(emptyMap())
    private val registered = MutableStateFlow<Map<String, ContentDescriptor>>(emptyMap())
    private val registration = Mutex()
    private val inspections = MutableStateFlow<Set<ContentDescriptor>>(emptySet())
    private var serial = 0L
    private val library = SourceContentLibrary(sources, CatalogCache(FileCacheStorage(catalogRoot), io))
    private val cache = RangeContentCache(FileCacheStorage(contentRoot), HttpRangeReader(http) { descriptor ->
        library.origin(descriptor) { key -> known.value[key] ?: audios.getAudioByPlaybackId(key.stableKey).first() }
    }, library::isEnabled, io = io, onCoverageChanged = { descriptor, complete ->
        CachedMediaAvailability.publish(descriptor.key, complete && cacheEnabled() && isCurrent(descriptor))
        inspections.update { it + descriptor }
    })
    private val filler = PlaybackCacheFiller(scope, cache) {
        co.touchlab.kermit.Logger.w("Native content fill deferred", it)
    }

    init {
        scope.launch {
            while (isActive) {
                inspections.first { it.isNotEmpty() }
                delay(250)
                val batch = inspections.getAndUpdate { emptySet() }
                batch.forEach { descriptor ->
                    try { inspect(descriptor) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { co.touchlab.kermit.Logger.w("Native cached metadata inspection failed", error) }
                }
            }
        }
        scope.launch {
            try {
                val saved = library.cachedSongs()
                restoreMetadata(saved.map { LAudio(id = it.key.id, title = it.title, subtitle = it.subtitle, mediaSourceName = it.key.sourceName, extra = it.metadata) })
                saved.mapNotNull { it.content }.forEach { descriptor ->
                    val handle = cache.open(descriptor)
                    try { CachedMediaAvailability.publish(descriptor.key, cacheEnabled() && isCurrent(descriptor) && handle.cachedEndFrom(0) == descriptor.size) }
                    finally { handle.close() }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { co.touchlab.kermit.Logger.w("Native cached catalog restoration failed", error) }
        }
        scope.launch {
            quotaMiB.collect {
                cache.updateQuota(it.coerceAtLeast(64) * 1024 * 1024)
                filler.retry()
            }
        }
        sources.sources.forEach { source ->
            scope.launch { source.contentState.collect {
                filler.retry(source.name)
                registered.value.values.filterNot(::isCurrent).forEach { CachedMediaAvailability.publish(it.key, false) }
            } }
            scope.launch {
                combine(source.snapshot, source.state) { snapshot, state -> snapshot.takeIf { state is SnapshotState.Success } }
                    .filterNotNull().distinctUntilChanged().collect { snapshot ->
                        known.update { it + snapshot.audios.associateBy(LAudio::mediaKey) }
                        try { library.rememberComplete(source.name, snapshot.audios) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { co.touchlab.kermit.Logger.w("Native catalog refresh failed", error) }
                    }
            }
            if (source is MediaSourcePatchSource) scope.launch { source.audioPatches.collect { audio ->
                known.update { it + (audio.mediaKey to audio) }
                try { library.remember(listOf(audio)) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { co.touchlab.kermit.Logger.w("Native catalog patch failed", error) }
            } }
        }
    }

    private fun isCurrent(descriptor: ContentDescriptor) = library.isEnabled(descriptor) &&
        (known.value[descriptor.key]?.let(library::descriptor)?.let { it == descriptor } ?: true)

    private suspend fun inspect(descriptor: ContentDescriptor) = withContext(io) {
        if (!isCurrent(descriptor)) return@withContext
        val audio = known.value[descriptor.key] ?: audios.getAudioByPlaybackId(descriptor.key.stableKey).first() ?: return@withContext
        val source = sources.findEnabledSource(descriptor.key.sourceName) ?: return@withContext
        val handle = cache.open(descriptor)
        try {
            val prefix = handle.cachedEndFrom(0)
            source.dataSource.inspectCachedMedia(audio, prefix, descriptor.size) { action ->
                val extension = audio.extra?.get("path")?.substringAfterLast('.', "")
                    ?.takeIf { it.length in 1..8 && it.all(Char::isLetterOrDigit) } ?: "audio"
                val file = Path(contentRoot, "inspect.$extension")
                try {
                    SystemFileSystem.sink(file).buffered().use { output ->
                        var offset = 0L
                        while (offset < prefix) {
                            ensureActive()
                            val bytes = handle.readCached(offset, minOf(64 * 1024L, prefix - offset).toInt()) ?: error("Cached prefix changed")
                            output.write(bytes); offset += bytes.size
                        }
                    }
                    if (isCurrent(descriptor)) action(file.toString())
                } finally { SystemFileSystem.delete(file, mustExist = false) }
            }
        } finally { handle.close() }
    }

    suspend fun resolve(audio: LAudio): MediaData {
        known.update { it + (audio.mediaKey to audio) }
        if (cacheEnabled()) {
            val descriptor = library.descriptor(audio) ?: library.cachedSongs().firstOrNull { it.key == audio.mediaKey }?.content
            if (descriptor != null && library.isEnabled(descriptor)) {
                // Catalog failures must not turn a playable network resource into a playback failure.
                try { if (library.descriptor(audio) != null) library.remember(listOf(audio)) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { co.touchlab.kermit.Logger.w("Native catalog write failed", error) }
                return MediaData.Url(registration.withLock {
                    registered.value.entries.firstOrNull { it.value == descriptor }?.key
                        ?: "lmusic-content://item/${++serial}".also { uri -> registered.update { it + (uri to descriptor) } }
                })
            }
        }
        return sources.resolveMediaData(audio)
    }

    fun descriptor(uri: String): ContentDescriptor? = registered.value[uri]
    fun selectCurrent(data: MediaData?) { filler.select((data as? MediaData.Url)?.url?.let(::descriptor)) }
    fun canPreloadAfter(data: MediaData): Boolean = (data as? MediaData.Url)?.url?.let(::descriptor)?.let(filler::isComplete) ?: true

    suspend fun open(uri: String): ContentHandle {
        val descriptor = descriptor(uri) ?: throw ContentUnavailable("Expired native content handle")
        val delegate = cache.open(descriptor)
        return object : ContentHandle by delegate {
            override suspend fun read(offset: Long, length: Int): ByteArray {
                delegate.readCached(offset, length)?.let { return it }
                return foregroundContentRead { filler.foregroundRead(descriptor, offset) { delegate.read(offset, length) } }
            }
        }
    }

    fun close() {
        filler.close(); scope.cancel(); http.close()
        registered.value.values.forEach { CachedMediaAvailability.publish(it.key, false) }
        registered.value = emptyMap(); known.value = emptyMap()
    }
}

/** Restore only absent, enabled-source rows; online metadata remains authoritative. */
internal suspend fun restoreNativeCachedMetadata(repository: AudioRepository, sources: PlatformMediaSource, cached: List<LAudio>) {
    val existing = repository.getAudios().first().map { it.playbackId }.toSet()
    val database = org.koin.mp.KoinPlatform.getKoin().get<com.lalilu.lmedia.data.database.ILMediaDatabase>()
    cached.filter { it.playbackId !in existing && sources.isEnabled(it.mediaSourceName) }
        .forEach { database.mediaDao().upsertAudio(it) }
}

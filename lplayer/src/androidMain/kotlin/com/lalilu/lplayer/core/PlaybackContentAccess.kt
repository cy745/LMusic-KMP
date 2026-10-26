package com.lalilu.lplayer.core

import android.content.Context
import androidx.media3.datasource.DefaultDataSource
import com.lalilu.lmedia.content.*
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.mediaKey
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.lalilu.lmedia.domain.repository.getAudioByPlaybackId
import com.lalilu.lmedia.domain.source.*
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.playback.core.PlayableResource

/** One foreground owner; cache metadata does not contain source URLs or credentials. */
class PlaybackContentAccess(private val context: Context, private val sources: PlatformMediaSource, private val audios: AudioRepository,
    private val storagePrefix: String = "plan-b",
    private val restoreMetadata: suspend (List<LAudio>) -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val temporary = ConcurrentHashMap.newKeySet<File>()
    private val http = HttpClient(OkHttp) {
        install(HttpTimeout) { connectTimeoutMillis=15000; socketTimeoutMillis=15000; requestTimeoutMillis=30000 }
    }
    private val catalog = CatalogCache(FileCacheStorage(File(context.filesDir,"$storagePrefix-catalog").path),Dispatchers.IO)
    private val library = SourceContentLibrary(sources,catalog)
    private val descriptors = ConcurrentHashMap<String,ContentDescriptor>()
    private val known = ConcurrentHashMap<com.lalilu.lmedia.domain.model.MediaKey,LAudio>()
    private val verified = ConcurrentHashMap<MediaKey,ContentDescriptor>()
    private val coverage = ConcurrentHashMap<ContentDescriptor, List<ByteSpan>>()
    private val pendingInspection = ConcurrentHashMap<ContentDescriptor, Unit>()
    private val inspectionSignals = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    private fun inspectLater(descriptor: ContentDescriptor) {
        pendingInspection[descriptor] = Unit
        inspectionSignals.trySend(Unit)
    }
    private val reader = HttpRangeReader(http) { descriptor ->
        library.origin(descriptor) { key ->
            known[key] ?: audios.getAudios().first().firstOrNull { it.mediaKey==key }?.also { known[key]=it }
        }
    }
    private val cache = RangeContentCache(FileCacheStorage(File(context.cacheDir,"$storagePrefix-content").path),reader,library::isEnabled,io=Dispatchers.IO,
        onCoverageChanged = { descriptor, complete ->
            if (complete && isCurrent(descriptor)) verified[descriptor.key]=descriptor
            else verified.remove(descriptor.key,descriptor)
            if (storagePrefix == "plan-b") publishAvailability(descriptor.key)
            inspectLater(descriptor)
        })
    init {
        scope.launch {
            for (signal in inspectionSignals) {
                delay(250) // Coalesce small decoder reads; tag work never runs on a loader or UI thread.
                val batch = pendingInspection.keys.toList()
                batch.forEach { descriptor ->
                    pendingInspection.remove(descriptor)
                    try { inspectCached(descriptor) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { co.touchlab.kermit.Logger.w("Cached media inspection failed", error) }
                }
            }
        }
        scope.launch {
            try {
                val saved = library.cachedSongs()
                restoreMetadata(saved.map { LAudio(id=it.key.id,title=it.title,subtitle=it.subtitle,mediaSourceName=it.key.sourceName,extra=it.metadata) })
                if (storagePrefix == "plan-b") saved.forEach { song ->
                    song.content?.let { descriptor ->
                        try {
                            val handle=cache.open(descriptor)
                            try {
                                if (handle.cachedEndFrom(0)==descriptor.size && isCurrent(descriptor)) verified[song.key]=descriptor
                                publishAvailability(song.key)
                            } finally { handle.close() }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { publishAvailability(song.key) }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { co.touchlab.kermit.Logger.w("Content catalog restoration failed",error) }
        }
        if (storagePrefix == "plan-b") scope.launch {
            com.lalilu.lplayer.LPlayerKV.contentCacheEnabled.flow().collect {
                verified.keys.forEach(::publishAvailability)
            }
        }
        if (storagePrefix == "plan-b") scope.launch {
            com.lalilu.lplayer.LPlayerKV.contentCacheQuotaMiB.flow().collect {
                try { cache.updateQuota((it.toIntOrNull() ?: 512).coerceAtLeast(64).toLong()*1024*1024) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { co.touchlab.kermit.Logger.w("Content cache quota update failed",error) }
            }
        }
        sources.sources.forEach { source ->
            scope.launch {
                source.contentState.collect {
                    verified.entries.filter { !library.isEnabled(it.value) }.forEach { (key,value) ->
                        verified.remove(key,value)
                        publishAvailability(key)
                    }
                }
            }
            scope.launch {
                combine(source.snapshot, source.state) { snapshot,state -> snapshot.takeIf { state is SnapshotState.Success } }
                    .filterNotNull().distinctUntilChanged().collect { snapshot ->
                        try {
                            snapshot.audios.forEach(::rememberKnown)
                            library.rememberComplete(source.name,snapshot.audios)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { co.touchlab.kermit.Logger.w("Content catalog refresh failed",error) }
                    }
            }
            if (source is MediaSourcePatchSource) scope.launch {
                source.audioPatches.collect { audio ->
                    rememberKnown(audio)
                    try { library.remember(listOf(audio)) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { co.touchlab.kermit.Logger.w("Content metadata cache update failed",error) }
                }
            }
        }
    }
    private suspend fun inspectCached(descriptor: ContentDescriptor) {
        if (!isCurrent(descriptor)) return
        val handle = cache.open(descriptor)
        try {
            val spans = handle.cachedSpans()
            coverage[descriptor] = spans
            val prefix = spans.firstOrNull { it.start == 0L }?.endExclusive ?: 0L
            val audio = known[descriptor.key] ?: audios.getAudioByPlaybackId(descriptor.key.stableKey).first() ?: return
            val source = sources.findEnabledSource(descriptor.key.sourceName) ?: return
            source.dataSource.inspectCachedMedia(audio, prefix, descriptor.size) { action ->
                if (!isCurrent(descriptor)) return@inspectCachedMedia
                val extension = audio.extra?.get("path")?.substringAfterLast('.', "")
                    ?.takeIf { it.length in 1..8 && it.all(Char::isLetterOrDigit) } ?: "audio"
                val file = File.createTempFile("metadata-", ".$extension", context.cacheDir)
                temporary += file
                try {
                    file.outputStream().use { output ->
                        var offset = 0L
                        while (offset < prefix) {
                            currentCoroutineContext().ensureActive()
                            val bytes = handle.read(offset, minOf(RangeContentCache.MAX_READ.toLong(), prefix - offset).toInt())
                            check(bytes.isNotEmpty())
                            output.write(bytes)
                            offset += bytes.size
                        }
                    }
                    if (isCurrent(descriptor)) action(file.path)
                } finally { file.delete(); temporary.remove(file) }
            }
        } finally { handle.close() }
    }

    /** Immutable byte ranges, mapped to time using the extractor's seek map, never file-size ratios. */
    fun cachedRanges(uri: String): Pair<Long, List<ByteSpan>>? = descriptors[uri]?.let { descriptor ->
        if (isCurrent(descriptor)) coverage[descriptor]?.let { descriptor.size to it } else null
    }

    private fun isCurrent(descriptor: ContentDescriptor): Boolean = library.isEnabled(descriptor) &&
        (known[descriptor.key]?.let(library::descriptor)?.let { it == descriptor } ?: true)
    private fun publishAvailability(key: MediaKey) {
        if (storagePrefix == "plan-b") CachedMediaAvailability.publish(key,
            com.lalilu.lplayer.LPlayerKV.contentCacheEnabled.value && verified[key]?.let(::isCurrent) == true)
    }
    private fun rememberKnown(audio: LAudio) {
        known[audio.mediaKey]=audio
        verified[audio.mediaKey]?.takeUnless(::isCurrent)?.let { verified.remove(audio.mediaKey,it) }
        publishAvailability(audio.mediaKey)
    }
    val dataSourceFactory = ContentDataSourceFactory(DefaultDataSource.Factory(context),cache) { uri ->
        descriptors[uri.toString()] ?: error("Expired content handle")
    }
    private fun register(descriptor: ContentDescriptor): String {
        // Opaque process-local token: the playback URI never contains a server path or credential.
        inspectLater(descriptor)
        val existing = descriptors.entries.firstOrNull { it.value==descriptor }?.key
        return existing ?: "lmusic-content://${UUID.randomUUID()}".also { descriptors[it]=descriptor }
    }
    suspend fun remember(items: List<LAudio>) {
        items.forEach(::rememberKnown)
        library.remember(items)
        // Only a successful source snapshot is authoritative; a partial page never removes absent rows.
        sources.enabledSources.forEach { source ->
            if (source.state.value is com.lalilu.lmedia.domain.source.SnapshotState.Success) {
                source.snapshot.value?.let { snapshot ->
                    library.rememberComplete(source.name,snapshot.audios)
                    snapshot.audios.forEach(::rememberKnown)
                }
            }
        }
    }
    fun uri(audio: LAudio): String? = if (com.lalilu.lplayer.LPlayerKV.contentCacheEnabled.value) library.descriptor(audio)?.let(::register) else null
    suspend fun resolve(key: MediaKey): PlayableResource = withContext(Dispatchers.IO) {
        val audio = audios.getAudioByPlaybackId(key.stableKey).first() ?: known[key]
        if (audio != null) {
            known[key]=audio
            uri(audio)?.let { return@withContext PlayableResource(it) }
        }
        if (com.lalilu.lplayer.LPlayerKV.contentCacheEnabled.value) library.cachedSongs().firstOrNull { it.key==key }?.content?.let { return@withContext PlayableResource(register(it)) }
        val item = audio ?: throw ContentUnavailable("Song is not present in the library or cached catalog")
        when (val data = sources.resolveMediaData(item)) {
            is MediaData.Url -> PlayableResource(data.url)
            is MediaData.Bytes -> {
                val file=File.createTempFile("playback-content-", ".audio",context.cacheDir)
                temporary += file
                file.writeBytes(data.bytes)
                PlayableResource(file.toURI().toString())
            }
        }
    }
    suspend fun cachedSongs(): List<Pair<LAudio,String>> = library.cachedSongs().mapNotNull { song ->
        song.content?.let { descriptor -> LAudio(id=song.key.id,title=song.title,subtitle=song.subtitle,mediaSourceName=song.key.sourceName,extra=song.metadata) to register(descriptor) }
    }
    fun close() { scope.cancel(); http.close(); descriptors.clear(); known.clear(); temporary.forEach { it.delete() }; temporary.clear(); if(storagePrefix=="plan-b") CachedMediaAvailability.reset() }
}

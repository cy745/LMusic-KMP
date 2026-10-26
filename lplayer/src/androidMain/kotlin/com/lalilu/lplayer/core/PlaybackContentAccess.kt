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
        })
    init {
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

package com.lalilu.lmusic.prototype

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

/** One foreground owner; cache metadata does not contain source URLs or credentials. */
class PrototypeContentAccess(context: Context, private val sources: PlatformMediaSource, private val audios: AudioRepository) {
    private val http = HttpClient(OkHttp) {
        install(HttpTimeout) { connectTimeoutMillis=15000; socketTimeoutMillis=15000; requestTimeoutMillis=30000 }
    }
    private val catalog = CatalogCache(FileCacheStorage(File(context.filesDir,"plan-b-catalog").path),Dispatchers.IO)
    private val library = SourceContentLibrary(sources,catalog)
    private val descriptors = ConcurrentHashMap<String,ContentDescriptor>()
    private val known = ConcurrentHashMap<com.lalilu.lmedia.domain.model.MediaKey,LAudio>()
    private val reader = HttpRangeReader(http) { descriptor ->
        library.origin(descriptor) { key ->
            known[key] ?: audios.getAudios().first().firstOrNull { it.mediaKey==key }?.also { known[key]=it }
        }
    }
    private val cache = RangeContentCache(FileCacheStorage(File(context.cacheDir,"plan-b-content").path),reader,library::isEnabled,io=Dispatchers.IO)
    val dataSourceFactory = PrototypeDataSourceFactory(DefaultDataSource.Factory(context),cache) { uri ->
        descriptors[uri.toString()] ?: error("Expired content handle")
    }
    private fun register(descriptor: ContentDescriptor): String {
        // Opaque process-local token: the playback URI never contains a server path or credential.
        val existing = descriptors.entries.firstOrNull { it.value==descriptor }?.key
        return existing ?: "lmusic-content://${UUID.randomUUID()}".also { descriptors[it]=descriptor }
    }
    suspend fun remember(items: List<LAudio>) {
        items.forEach { known[it.mediaKey]=it }
        library.remember(items)
        // Only a successful source snapshot is authoritative; a partial page never removes absent rows.
        sources.enabledSources.forEach { source ->
            if (source.state.value is com.lalilu.lmedia.domain.source.SnapshotState.Success) {
                source.snapshot.value?.let { snapshot ->
                    library.rememberComplete(source.name,snapshot.audios)
                    snapshot.audios.forEach { known[it.mediaKey]=it }
                }
            }
        }
    }
    fun uri(audio: LAudio): String? = library.descriptor(audio)?.let(::register)
    suspend fun cachedSongs(): List<Pair<LAudio,String>> = library.cachedSongs().mapNotNull { song ->
        song.content?.let { descriptor -> LAudio(id=song.key.id,title=song.title,subtitle=song.subtitle,mediaSourceName=song.key.sourceName,extra=song.metadata) to register(descriptor) }
    }
    fun close() { http.close(); descriptors.clear(); known.clear() }
}

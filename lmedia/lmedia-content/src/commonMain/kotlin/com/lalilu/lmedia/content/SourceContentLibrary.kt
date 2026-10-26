package com.lalilu.lmedia.content

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.lmedia.domain.model.mediaKey
import com.lalilu.lmedia.domain.source.*

/** Compatibility bridge: source-specific discovery stays outside the generic cache. */
class SourceContentLibrary(private val sources: PlatformMediaSource, private val catalog: CatalogCache) {
    private fun qualifiedScope(source: MediaSource): String? = source.dataSource.contentCacheScope()?.let { "${source.name.length}:${source.name}$it" }
    private val displayKeys = setOf("artistId", "artistName", "albumId", "albumName", "albumArtist", "genre", "composer", "lyricist", "track", "disc", "date", "duration", "dateAdded", "dateModified")
    private fun song(audio: LAudio) = CatalogSong(audio.mediaKey, audio.title, audio.subtitle, descriptor(audio), audio.extra.orEmpty().filterKeys { it in displayKeys })
    fun descriptor(audio: LAudio): ContentDescriptor? {
        val source = sources.findEnabledSource(audio.mediaSourceName) ?: return null
        val identity = source.dataSource.contentCacheIdentity(audio) ?: return null
        if (identity.scope != source.dataSource.contentCacheScope()) return null
        return ContentDescriptor(audio.mediaKey, qualifiedScope(source)!!, identity.revision, identity.size, identity.etag, identity.lastModified)
    }
    fun isEnabled(descriptor: ContentDescriptor): Boolean = sources.findEnabledSource(descriptor.key.sourceName)
        ?.let(::qualifiedScope) == descriptor.scope

    /** Used for partial library pages: absence never means deletion. Full scans must opt into Complete. */
    suspend fun remember(audios: List<LAudio>, coverage: ScanCoverage = ScanCoverage.Partial) {
        audios.groupBy { it.mediaSourceName }.forEach { (name, items) ->
            val source = sources.findEnabledSource(name) ?: return@forEach
            val scope = qualifiedScope(source) ?: return@forEach
            if (!catalog.snapshot(scope).enabled) catalog.setEnabled(scope, true)
            val refresh = catalog.beginRefresh(scope)
            val songs = items.distinctBy { it.mediaKey }.map(::song)
            if (sources.isEnabled(source) && qualifiedScope(source) == scope) catalog.commit(refresh, songs, coverage)
        }
    }
    /** Explicit empty full results can remove a source's catalog too. Failed scans never call this. */
    suspend fun rememberComplete(sourceName: String, audios: List<LAudio>) {
        require(audios.all { it.mediaSourceName == sourceName })
        val source = sources.findEnabledSource(sourceName) ?: return
        val scope = qualifiedScope(source) ?: return
        if (!catalog.snapshot(scope).enabled) catalog.setEnabled(scope, true)
        val refresh = catalog.beginRefresh(scope)
        val songs = audios.distinctBy { it.mediaKey }.map(::song)
        if (sources.isEnabled(source) && qualifiedScope(source) == scope) catalog.commit(refresh,songs,ScanCoverage.Complete)
    }
    /** No readiness wait and no network. Disabled sources vanish immediately from this projection. */
    suspend fun cachedSongs(): List<CatalogSong> {
        val result = mutableListOf<CatalogSong>()
        sources.sources.forEach { source ->
            val scope = qualifiedScope(source) ?: return@forEach
            val enabled = sources.isEnabled(source)
            if (catalog.snapshot(scope).enabled != enabled) catalog.setEnabled(scope, enabled)
            if (enabled) {
                val saved = catalog.visible(scope).filter { it.key.sourceName == source.name }
                if (sources.isEnabled(source) && qualifiedScope(source) == scope) result += saved
            }
        }
        return result
    }
    /** Invoked only for missing byte ranges, after any cached ranges have been served. */
    suspend fun origin(descriptor: ContentDescriptor, lookup: suspend (MediaKey) -> LAudio?): ContentOrigin {
        check(isEnabled(descriptor)) { "Source configuration changed or disabled" }
        val source = sources.findEnabledSource(descriptor.key.sourceName) ?: throw ContentUnavailable("Source disabled")
        val audio = lookup(descriptor.key) ?: throw ContentUnavailable("No online source locator; cached ranges remain available")
        check(this.descriptor(audio) == descriptor) { "Content revision changed; reopen the current catalog item" }
        source.requireContentReady(15_000)
        check(isEnabled(descriptor)) { "Source disabled during preparation" }
        val data = source.dataSource.getMedia(audio) as? MediaData.Url ?: throw ContentUnavailable("Source has no ranged URL")
        check(isEnabled(descriptor) && this.descriptor(audio) == descriptor) { "Content configuration changed during resolution" }
        require(data.url.startsWith("https://", true) || data.url.startsWith("http://", true)) { "Range transport requires HTTP" }
        return ContentOrigin(data.url, source.dataSource.originHeaders(audio))
    }
}

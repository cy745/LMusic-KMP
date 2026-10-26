package com.lalilu.lmedia.content

import com.lalilu.lmedia.domain.model.MediaKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Display metadata only. Access URLs and credentials belong to the runtime source adapter. */
@Serializable
data class CatalogSong(val key: MediaKey, val title: String, val subtitle: String = "", val content: ContentDescriptor? = null, val metadata: Map<String, String> = emptyMap())
@Serializable
data class CatalogSnapshot(val scope: String, val generation: Long = 0, val enabled: Boolean = true, val songs: List<CatalogSong> = emptyList())
data class CatalogRefresh internal constructor(val scope: String, val generation: Long)
enum class ScanCoverage { Complete, Partial }

/** Transactional cached directory. Partial scans only upsert; only explicitly complete scans can remove songs. */
class CatalogCache(private val storage: CacheStorage, private val io: CoroutineDispatcher = Dispatchers.Default) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private fun path(scope: String) = "catalog-${digest(scope)}.json"
    private fun load(scope: String): CatalogSnapshot {
        // IO failure must not turn a valid old directory into an empty successful result.
        val bytes = storage.read(path(scope)) ?: return CatalogSnapshot(scope)
        return runCatching { json.decodeFromString<CatalogSnapshot>(bytes.decodeToString()) }
            .getOrNull()?.takeIf { it.scope == scope } ?: CatalogSnapshot(scope)
    }
    private fun save(value: CatalogSnapshot) = storage.writeAtomic(path(value.scope), json.encodeToString(value).encodeToByteArray())
    suspend fun snapshot(scope: String): CatalogSnapshot = withContext(io) { mutex.withLock { load(scope) } }
    suspend fun visible(scope: String): List<CatalogSong> = snapshot(scope).let { if (it.enabled) it.songs else emptyList() }
    suspend fun setEnabled(scope: String, enabled: Boolean) = withContext(io) {
        mutex.withLock { val old = load(scope); save(old.copy(enabled = enabled, generation = old.generation + 1)) }
    }
    suspend fun beginRefresh(scope: String): CatalogRefresh = withContext(io) {
        require(scope.isNotBlank())
        mutex.withLock {
            val old = load(scope)
            check(old.enabled) { "Source is disabled" }
            val next = old.copy(generation = old.generation + 1)
            save(next)
            CatalogRefresh(scope, next.generation)
        }
    }
    suspend fun commit(refresh: CatalogRefresh, songs: List<CatalogSong>, coverage: ScanCoverage): Boolean = withContext(io) {
        require(songs.all { it.content == null || (it.content.scope == refresh.scope && it.content.key == it.key) })
        require(songs.map { it.key }.distinct().size == songs.size) { "Duplicate source-qualified songs" }
        mutex.withLock {
            val old = load(refresh.scope)
            if (!old.enabled || old.generation != refresh.generation) return@withLock false
            val merged = if (coverage == ScanCoverage.Complete) songs else {
                old.songs.associateBy { it.key }.toMutableMap().apply { songs.forEach { put(it.key, it) } }.values.toList()
            }
            save(old.copy(songs = merged, generation = old.generation + 1))
            true
        }
    }
}

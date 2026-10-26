package com.lalilu.lmedia.content

import com.lalilu.lmedia.domain.model.MediaKey
import kotlinx.serialization.Serializable

/** scope changes on account/server/root changes; revision changes on content replacement. Never a signed URL. */
@Serializable
data class ContentDescriptor(
    val key: MediaKey,
    val scope: String,
    val revision: String,
    val size: Long,
    val etag: String? = null,
    val lastModified: String? = null,
) {
    init { require(scope.isNotBlank() && revision.isNotBlank() && size > 0) }
}

@Serializable
data class ByteSpan(val start: Long, val endExclusive: Long) {
    init { require(start >= 0 && endExclusive > start) }
}

/** Runtime-only credentials/URLs; never persisted by this module. */
data class ContentOrigin(val url: String, val headers: Map<String, String> = emptyMap())

fun interface RangeReader {
    /** Return exactly length bytes or throw. Must verify remote representation and range semantics. */
    suspend fun read(descriptor: ContentDescriptor, offset: Long, length: Int): ByteArray
}

interface ContentHandle {
    val descriptor: ContentDescriptor
    /** EOF returns empty; reads are bounded to 1 MiB so large files never enter memory as a whole. */
    suspend fun read(offset: Long, length: Int): ByteArray
    /** Returns null on a hole; must not fetch or wait for an unrelated in-flight network read. */
    suspend fun readCached(offset: Long, length: Int): ByteArray? = null
    suspend fun cachedSpans(): List<ByteSpan>
    /** Only the continuous cached region containing offset, never the sum of disjoint spans. */
    suspend fun cachedEndFrom(offset: Long): Long
    suspend fun close()
}

class ContentUnavailable(message: String) : IllegalStateException(message)

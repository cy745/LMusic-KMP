package com.lalilu.lmedia.content

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray

/** Resolves fresh credentials only on a cache miss. Never logs or persists the origin URL. */
class HttpRangeReader(
    private val client: HttpClient,
    private val origin: suspend (ContentDescriptor) -> ContentOrigin,
) : RangeReader {
    override suspend fun read(descriptor: ContentDescriptor, offset: Long, length: Int): ByteArray {
        require(offset >= 0 && length in 1..RangeContentCache.MAX_READ && offset <= descriptor.size - length)
        val target = origin(descriptor)
        val end = offset + length - 1
        return client.prepareGet(target.url) {
            target.headers.forEach { (key, value) -> header(key, value) }
            header(HttpHeaders.AcceptEncoding, "identity")
            header(HttpHeaders.Range, "bytes=$offset-$end")
            descriptor.etag?.takeUnless { it.startsWith("W/") }?.let { header(HttpHeaders.IfMatch, it) }
                ?: descriptor.lastModified?.let { header(HttpHeaders.IfUnmodifiedSince, it) }
        }.execute { response ->
            check(response.status == HttpStatusCode.PartialContent ||
                (response.status == HttpStatusCode.OK && offset == 0L && length.toLong() == descriptor.size)) {
                "Expected a validated byte range, received HTTP ${response.status.value}"
            }
            check(response.headers[HttpHeaders.ContentEncoding].let { it == null || it.equals("identity", true) }) { "Encoded response cannot be cached by byte offset" }
            if (response.status == HttpStatusCode.PartialContent) {
                val range = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(response.headers[HttpHeaders.ContentRange].orEmpty())
                    ?: error("Missing or invalid Content-Range")
                check(range.groupValues[1].toLong() == offset && range.groupValues[2].toLong() == end && range.groupValues[3].toLong() == descriptor.size) { "Content range or total size changed" }
            }
            descriptor.etag?.let { expected ->
                check(response.headers[HttpHeaders.ETag] == expected) { "Missing or changed content ETag" }
            }
            if (descriptor.etag == null) descriptor.lastModified?.let { expected ->
                check(response.headers[HttpHeaders.LastModified] == expected) { "Missing or changed content modification time" }
            }
            response.headers[HttpHeaders.ContentLength]?.let { check(it.toLongOrNull() == length.toLong()) { "Wrong range length" } }
            val channel = response.bodyAsChannel()
            val bytes = channel.readBuffer(length + 1).readByteArray()
            check(bytes.size == length) { "Truncated or oversized byte range" }
            bytes
        }
    }
}

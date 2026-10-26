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
            val status = response.status.value
            if (status != 206 && !(status == 200 && offset == 0L && length.toLong() == descriptor.size)) {
                val kind = when (status) {
                    401, 403 -> ContentFailureKind.Authentication
                    404, 410 -> ContentFailureKind.Missing
                    412, 416 -> ContentFailureKind.Changed
                    408, 429, in 500..599 -> ContentFailureKind.Temporary
                    else -> ContentFailureKind.InvalidResponse
                }
                val message = when (kind) {
                    ContentFailureKind.Authentication -> "Source authentication failed; check account credentials"
                    ContentFailureKind.Missing -> "The source file is no longer available"
                    ContentFailureKind.Changed -> "The source file changed; refresh the library before retrying"
                    ContentFailureKind.Temporary -> "Source temporarily unavailable"
                    ContentFailureKind.InvalidResponse -> "Source did not return a valid byte range"
                }
                throw ContentReadFailure(kind, status, "$message (HTTP $status)")
            }
            requireValidContent(response.headers[HttpHeaders.ContentEncoding].let { it == null || it.equals("identity", true) }, "Encoded response cannot be cached by byte offset")
            if (response.status == HttpStatusCode.PartialContent) {
                val range = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(response.headers[HttpHeaders.ContentRange].orEmpty())
                    ?: throw ContentReadFailure(ContentFailureKind.InvalidResponse, message = "Missing or invalid Content-Range")
                requireValidContent(range.groupValues[1].toLongOrNull() == offset && range.groupValues[2].toLongOrNull() == end && range.groupValues[3].toLongOrNull() == descriptor.size, "Content range or total size changed")
            }
            descriptor.etag?.let { expected ->
                if (response.headers[HttpHeaders.ETag] != expected) throw ContentReadFailure(ContentFailureKind.Changed, message = "Missing or changed content ETag")
            }
            if (descriptor.etag == null) descriptor.lastModified?.let { expected ->
                if (response.headers[HttpHeaders.LastModified] != expected) throw ContentReadFailure(ContentFailureKind.Changed, message = "Missing or changed content modification time")
            }
            response.headers[HttpHeaders.ContentLength]?.let { requireValidContent(it.toLongOrNull() == length.toLong(), "Wrong range length") }
            val channel = response.bodyAsChannel()
            val bytes = channel.readBuffer(length + 1).readByteArray()
            requireValidContent(bytes.size == length, "Truncated or oversized byte range")
            bytes
        }
    }
}

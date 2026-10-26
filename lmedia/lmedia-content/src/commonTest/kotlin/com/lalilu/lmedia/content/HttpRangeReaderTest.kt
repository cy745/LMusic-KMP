package com.lalilu.lmedia.content

import com.lalilu.lmedia.domain.model.MediaKey
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class HttpRangeReaderTest {
    private val d=ContentDescriptor(MediaKey("source","song"),"scope","v1",1000,etag="\"v1\"")
    @Test fun validatesRangeIdentityAndUsesFreshCredentialsOnMiss() = runTest {
        var resolves=0
        val client=HttpClient(MockEngine { request ->
            assertEquals("bytes=500-599",request.headers[HttpHeaders.Range])
            assertEquals("\"v1\"",request.headers[HttpHeaders.IfMatch])
            assertEquals("Bearer token",request.headers[HttpHeaders.Authorization])
            respond(ByteArray(100){it.toByte()},HttpStatusCode.PartialContent,headersOf(
                HttpHeaders.ContentRange to listOf("bytes 500-599/1000"), HttpHeaders.ETag to listOf("\"v1\"")))
        })
        try {
            val reader=HttpRangeReader(client) { resolves++; ContentOrigin("https://example.test/song",mapOf(HttpHeaders.Authorization to "Bearer token")) }
            assertEquals(100,reader.read(d,500,100).size); assertEquals(1,resolves)
        } finally { client.close() }
    }
    @Test fun rejectsIgnoredMalformedShiftedTruncatedAndChangedRepresentations() = runTest {
        data class Case(val status: HttpStatusCode=HttpStatusCode.PartialContent, val range: String?="bytes 500-599/1000",val etag:String?="\"v1\"",val bytes:Int=100,val encoding:String?=null)
        val cases=listOf(
            Case(status=HttpStatusCode.OK), Case(range=null), Case(range="bytes 0-99/1000"),
            Case(range="bytes 500-599/1100"),Case(etag="\"v2\""),Case(etag=null),Case(bytes=99),Case(bytes=101),Case(encoding="gzip"),
            Case(status=HttpStatusCode.PreconditionFailed)
        )
        for(case in cases) {
            val client=HttpClient(MockEngine {
                respond(ByteArray(case.bytes),case.status,Headers.build {
                    case.range?.let { append(HttpHeaders.ContentRange,it) }
                    case.etag?.let { append(HttpHeaders.ETag,it) }
                    case.encoding?.let { append(HttpHeaders.ContentEncoding,it) }
                })
            })
            try {
                assertFailsWith<IllegalStateException>("$case") { HttpRangeReader(client) { ContentOrigin("https://example.test/song") }.read(d,500,100) }
            } finally { client.close() }
        }
    }
}

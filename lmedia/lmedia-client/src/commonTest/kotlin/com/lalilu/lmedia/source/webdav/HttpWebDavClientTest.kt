package com.lalilu.lmedia.source.webdav

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.util.encodeBase64
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** HTTP 层：请求形状（方法/头/体）、状态码映射与路径编码。 */
class HttpWebDavClientTest {

    private val config = WebDavConfig(
        url = "http://127.0.0.1:8080/",
        username = "lmusic",
        password = "secret",
        rootPath = "/Music/",
    )

    private val multiStatus = """
        <?xml version="1.0" encoding="utf-8"?>
        <D:multistatus xmlns:D="DAV:">
        <D:response>
        <D:href>/Music/</D:href>
        <D:propstat>
        <D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
        <D:status>HTTP/1.1 200 OK</D:status>
        </D:propstat>
        </D:response>
        <D:response>
        <D:href>/Music/02%20Friend.flac</D:href>
        <D:propstat>
        <D:prop>
        <D:resourcetype/>
        <D:getcontentlength>32239619</D:getcontentlength>
        <D:getetag>"1ebf003"</D:getetag>
        <D:getcontenttype>audio/x-flac</D:getcontenttype>
        </D:prop>
        <D:status>HTTP/1.1 200 OK</D:status>
        </D:propstat>
        </D:response>
        </D:multistatus>
    """.trimIndent()

    private fun clientOf(
        status: HttpStatusCode = HttpStatusCode.MultiStatus,
        body: String = multiStatus,
        onRequest: (HttpRequestData) -> Unit = {},
    ): HttpWebDavClient {
        val engine = MockEngine { request ->
            onRequest(request)
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/xml"),
            )
        }
        return HttpWebDavClient(config, HttpClient(engine))
    }

    @Test
    fun `sends propfind with depth basic auth and query body`() = runTest {
        var captured: HttpRequestData? = null
        val client = clientOf(onRequest = { captured = it })

        val entries = client.propfind("/Music/", depth = 1)

        val request = assertTrue(captured != null).let { captured!! }
        assertEquals(HttpMethod("PROPFIND"), request.method)
        assertEquals("1", request.headers["Depth"])
        assertEquals(
            "Basic " + "lmusic:secret".encodeToByteArray().encodeBase64(),
            request.headers[HttpHeaders.Authorization],
        )
        val body = request.body
        assertTrue(body is TextContent, "PROPFIND 必须带请求体，实际 ${body::class.simpleName}")
        assertTrue((body as TextContent).text.contains("getcontentlength"))

        assertEquals(2, entries.size)
        assertEquals("/Music/02 Friend.flac", entries[1].path)
        assertEquals(32_239_619L, entries[1].contentLength)
    }

    @Test
    fun `percent encodes spaces and non ascii characters in the request path`() = runTest {
        var captured: HttpRequestData? = null
        val client = clientOf(onRequest = { captured = it })

        client.propfind("/Music/HoneyComeBear/02 Friend.flac", depth = 0)

        val encoded = assertTrue(captured != null).let { captured!!.url.encodedPath }
        assertTrue(encoded.contains("%20"), "空格应被编码：$encoded")
        assertTrue(encoded.startsWith("/Music/"), "库根路径应被保留：$encoded")
    }

    @Test
    fun `omits authorization header when no credentials are configured`() = runTest {
        var captured: HttpRequestData? = null
        val anonymous = WebDavConfig(url = "http://127.0.0.1:8080")
        val engine = MockEngine { request ->
            captured = request
            respond(multiStatus, HttpStatusCode.MultiStatus)
        }
        val client = HttpWebDavClient(anonymous, HttpClient(engine))

        client.propfind("/", depth = 1)

        assertNull(assertTrue(captured != null).let { captured!!.headers[HttpHeaders.Authorization] })
    }

    @Test
    fun `maps failure status codes to user facing reasons`() = runTest {
        assertFailsWith<WebDavException.Unauthorized> {
            clientOf(status = HttpStatusCode.Unauthorized).propfind("/Music/", 1)
        }
        assertFailsWith<WebDavException.Forbidden> {
            clientOf(status = HttpStatusCode.Forbidden).propfind("/Music/", 1)
        }
        assertFailsWith<WebDavException.NotFound> {
            clientOf(status = HttpStatusCode.NotFound).propfind("/Music/", 1)
        }
        val unexpected = assertFailsWith<WebDavException.Unexpected> {
            clientOf(status = HttpStatusCode.InternalServerError).propfind("/Music/", 1)
        }
        assertTrue(unexpected.message.orEmpty().contains("WebDAV 请求失败"))
    }

    @Test
    fun `maps an unparsable body to an explicit failure`() = runTest {
        // 网关用 200 返回 HTML 错误页时不能退化成"这个目录是空的"
        val client = clientOf(status = HttpStatusCode.OK, body = "<html>Not Found</html>")

        assertFailsWith<WebDavException.Unexpected> { client.propfind("/Music/", 1) }
    }

    @Test
    fun `download returns null for a missing file instead of throwing`() = runTest {
        val client = clientOf(status = HttpStatusCode.NotFound, body = "")

        assertNull(client.download("/Music/.lmusic/meta/missing.json"))
    }

    @Test
    fun `downloads raw bytes for an existing file`() = runTest {
        val client = clientOf(status = HttpStatusCode.OK, body = """{"title":"Friend"}""")

        val bytes = client.download("/Music/.lmusic/meta/abc.json")

        assertEquals("""{"title":"Friend"}""", bytes?.decodeToString())
    }

    @Test
    fun `put sends bytes with the requested content type`() = runTest {
        var captured: HttpRequestData? = null
        val client = clientOf(
            status = HttpStatusCode.Created,
            body = "",
            onRequest = { captured = it },
        )

        client.put("/Music/.lmusic/meta/abc.json", "{}".encodeToByteArray(), "application/json")

        val request = assertTrue(captured != null).let { captured!! }
        assertEquals(HttpMethod.Put, request.method)
        val body = request.body
        assertTrue(body is ByteArrayContent, "PUT 应携带字节体，实际 ${body::class.simpleName}")
        assertEquals("application/json", (body as ByteArrayContent).contentType?.toString())
    }
}

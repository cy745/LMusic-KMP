package com.lalilu.lmedia.source.webdav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * PROPFIND 解析的回归测试。
 *
 * 夹具取自真实 Apache httpd（`bytemark/webdav` 镜像）的响应：它把同一个 `DAV:` 命名空间同时绑到
 * `D:` 与 `lp1:` 两个前缀，并且在 `<prop>` 中夹带 `supportedlock`、`lockdiscovery`、`lp2:executable`
 * 等无关元素——这些都是最容易把朴素的按前缀解析写崩的地方。
 */
class WebDavXmlTest {

    private val apacheResponse = """
        <?xml version="1.0" encoding="utf-8"?>
        <D:multistatus xmlns:D="DAV:">
        <D:response xmlns:lp1="DAV:" xmlns:lp2="http://apache.org/dav/props/">
        <D:href>/HoneyComeBear/</D:href>
        <D:propstat>
        <D:prop>
        <lp1:resourcetype><D:collection/></lp1:resourcetype>
        <lp1:creationdate>2026-09-21T05:47:09Z</lp1:creationdate>
        <lp1:getlastmodified>Mon, 21 Sep 2026 05:46:51 GMT</lp1:getlastmodified>
        <lp1:getetag>"1000-65bf7c6dbf8dc"</lp1:getetag>
        <D:supportedlock>
        <D:lockentry>
        <D:lockscope><D:exclusive/></D:lockscope>
        <D:locktype><D:write/></D:locktype>
        </D:lockentry>
        <D:lockentry>
        <D:lockscope><D:shared/></D:lockscope>
        <D:locktype><D:write/></D:locktype>
        </D:lockentry>
        </D:supportedlock>
        <D:lockdiscovery/>
        <D:getcontenttype>httpd/unix-directory</D:getcontenttype>
        </D:prop>
        <D:status>HTTP/1.1 200 OK</D:status>
        </D:propstat>
        </D:response>
        <D:response xmlns:lp1="DAV:" xmlns:lp2="http://apache.org/dav/props/">
        <D:href>/HoneyComeBear/02%20Friend.flac</D:href>
        <D:propstat>
        <D:prop>
        <lp1:resourcetype/>
        <lp1:creationdate>2026-09-21T05:47:09Z</lp1:creationdate>
        <lp1:getcontentlength>32239619</lp1:getcontentlength>
        <lp1:getlastmodified>Wed, 24 Sep 2025 05:14:28 GMT</lp1:getlastmodified>
        <lp1:getetag>"1ebf003-63f852074f500"</lp1:getetag>
        <lp2:executable>T</lp2:executable>
        <D:supportedlock/>
        <D:lockdiscovery/>
        <D:getcontenttype>audio/x-flac</D:getcontenttype>
        </D:prop>
        <D:status>HTTP/1.1 200 OK</D:status>
        </D:propstat>
        </D:response>
        <D:response xmlns:lp1="DAV:" xmlns:lp2="http://apache.org/dav/props/">
        <D:href>/HoneyComeBear/03%20%e3%81%95%e3%82%88%e3%81%aa%e3%82%89%e3%81%ae%e6%94%af%e5%ba%a6.flac</D:href>
        <D:propstat>
        <D:prop>
        <lp1:resourcetype/>
        <lp1:getcontentlength>25483547</lp1:getcontentlength>
        <lp1:getetag>"184d91b-63b0e42966f40"</lp1:getetag>
        <D:getcontenttype>audio/x-flac</D:getcontenttype>
        </D:prop>
        <D:status>HTTP/1.1 200 OK</D:status>
        </D:propstat>
        </D:response>
        </D:multistatus>
    """.trimIndent()

    @Test
    fun `parses every response and keeps values across nested lock elements`() {
        val entries = WebDavXml.parseMultiStatus(apacheResponse)

        assertEquals(3, entries.size)

        val directory = entries[0]
        assertEquals("/HoneyComeBear/", directory.path)
        assertTrue(directory.isDirectory)
        assertEquals("httpd/unix-directory", directory.contentType)

        val first = entries[1]
        assertEquals("/HoneyComeBear/02 Friend.flac", first.path)
        assertFalse(first.isDirectory)
        assertEquals(32_239_619L, first.contentLength)
        assertEquals("\"1ebf003-63f852074f500\"", first.etag)
        assertEquals("Wed, 24 Sep 2025 05:14:28 GMT", first.lastModified)
        assertEquals("audio/x-flac", first.contentType)
    }

    @Test
    fun `decodes lowercase percent escapes for non ascii names`() {
        val entries = WebDavXml.parseMultiStatus(apacheResponse)

        assertEquals("/HoneyComeBear/03 さよならの支度.flac", entries[2].path)
    }

    @Test
    fun `treats empty resourcetype as a file`() {
        val entries = WebDavXml.parseMultiStatus(apacheResponse)

        assertFalse(entries[1].isDirectory)
        assertFalse(entries[2].isDirectory)
    }

    @Test
    fun `accepts absolute href urls`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>http://127.0.0.1:8080/Music/a%20b.mp3</D:href>
                <D:propstat>
                  <D:prop><D:resourcetype/><D:getcontentlength>10</D:getcontentlength></D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
              <D:response>
                <D:href>https://dav.example.com/</D:href>
                <D:propstat>
                  <D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        val entries = WebDavXml.parseMultiStatus(xml)

        assertEquals("/Music/a b.mp3", entries[0].path)
        assertEquals("/", entries[1].path)
        assertTrue(entries[1].isDirectory)
    }

    @Test
    fun `prefers the ok propstat when the server also returns 404 for a property`() {
        val xml = """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:" xmlns:s="http://sabredav.org/ns">
              <d:response>
                <d:href>/remote.php/dav/files/user/Music/track.flac</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype/>
                    <d:getcontentlength>12345</d:getcontentlength>
                    <d:getcontenttype>audio/flac</d:getcontenttype>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
                <d:propstat>
                  <d:prop><d:getetag/></d:prop>
                  <d:status>HTTP/1.1 404 Not Found</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val entries = WebDavXml.parseMultiStatus(xml)

        assertEquals(1, entries.size)
        assertEquals(12_345L, entries[0].contentLength)
        assertEquals("audio/flac", entries[0].contentType)
        assertNull(entries[0].etag, "404 段里的 getetag 不应被当成有效值")
    }

    @Test
    fun `tolerates elements without a namespace declaration`() {
        val xml = """
            <?xml version="1.0"?>
            <multistatus>
              <response>
                <href>/a/b.mp3</href>
                <propstat>
                  <prop><resourcetype/><getcontentlength>7</getcontentlength></prop>
                  <status>HTTP/1.1 200 OK</status>
                </propstat>
              </response>
            </multistatus>
        """.trimIndent()

        val entries = WebDavXml.parseMultiStatus(xml)

        assertEquals(1, entries.size)
        assertEquals("/a/b.mp3", entries[0].path)
        assertEquals(7L, entries[0].contentLength)
    }

    @Test
    fun `returns empty list for blank input`() {
        assertTrue(WebDavXml.parseMultiStatus("").isEmpty())
        assertTrue(WebDavXml.parseMultiStatus("   ").isEmpty())
    }

    @Test
    fun `throws on truncated xml instead of silently returning an empty library`() {
        // 静默返回空列表会让上层把媒体库清空，因此截断的响应必须显式失败。
        assertFailsWith<Exception> {
            WebDavXml.parseMultiStatus("<D:multistatus xmlns:D=\"DAV:\"><D:response>")
        }
    }
}

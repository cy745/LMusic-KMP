package com.lalilu.lmedia.source.webdav

import io.ktor.http.decodeURLPart
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.XmlStreaming

/**
 * `PROPFIND` 的 `207 Multi-Status` 响应解析。
 *
 * 用拉取式解析而不是按前缀反序列化：真实服务器会把同一个 `DAV:` 命名空间绑到不同前缀
 * （Apache httpd 同时使用 `D:` 与 `lp1:`），按 localName + 命名空间匹配才不会漏字段。
 *
 * 每个子元素都必须被**完整消费**（[skipElement] 对空元素同样成立），否则外层循环会把子元素的
 * 结束标签误判成自己的结束，导致整个响应错位。
 */
internal object WebDavXml {
    private const val DAV_NAMESPACE = "DAV:"

    private class PropStat(
        val props: Map<String, String>,
        val isCollection: Boolean,
        val isOk: Boolean,
    )

    fun parseMultiStatus(xml: String): List<WebDavEntry> {
        if (xml.isBlank()) return emptyList()

        val reader = XmlStreaming.newReader(xml)
        try {
            val entries = mutableListOf<WebDavEntry>()
            var rootChecked = false
            while (true) {
                when (reader.next()) {
                    EventType.START_ELEMENT -> {
                        // 根元素必须是 multistatus：反代/网盘网关常用 200 返回 HTML 错误页，
                        // 若不校验，那种响应会被静默当成"空目录"，进而清空媒体库。
                        if (!rootChecked) {
                            rootChecked = true
                            if (reader.localName != "multistatus") {
                                throw WebDavException.Unexpected(
                                    "服务器返回的内容不是 WebDAV 多状态响应"
                                )
                            }
                        }
                        if (reader.isDav("response")) readResponse(reader)?.let(entries::add)
                    }

                    EventType.END_DOCUMENT -> return entries
                    else -> Unit
                }
            }
        } finally {
            reader.close()
        }
    }

    /** 位置：`<response>` 的 START_ELEMENT；返回时已消费到它的 END_ELEMENT。 */
    private fun readResponse(reader: XmlReader): WebDavEntry? {
        var href: String? = null
        val propStats = mutableListOf<PropStat>()

        while (true) {
            when (reader.next()) {
                EventType.START_ELEMENT -> when {
                    reader.isDav("href") -> href = readText(reader)
                    reader.isDav("propstat") -> propStats += readPropStat(reader)
                    else -> skipElement(reader)
                }

                EventType.END_ELEMENT, EventType.END_DOCUMENT -> break
                else -> Unit
            }
        }

        val path = href?.let(::toPath)?.takeIf(String::isNotBlank) ?: return null

        // 只信任 200 的 propstat；服务器对不支持的属性会单独返回 404 段。
        val stat = propStats.firstOrNull { it.isOk } ?: propStats.firstOrNull()
        return WebDavEntry(
            path = path,
            isDirectory = stat?.isCollection ?: path.endsWith('/'),
            contentLength = stat?.props?.get("getcontentlength")?.trim()?.toLongOrNull() ?: 0L,
            etag = stat?.props?.get("getetag")?.trim()?.takeIf(String::isNotBlank),
            lastModified = stat?.props?.get("getlastmodified")?.trim()?.takeIf(String::isNotBlank),
            contentType = stat?.props?.get("getcontenttype")?.trim()?.takeIf(String::isNotBlank),
        )
    }

    /** 位置：`<propstat>` 的 START_ELEMENT。 */
    private fun readPropStat(reader: XmlReader): PropStat {
        var props: Map<String, String> = emptyMap()
        var isCollection = false
        var isOk = true
        var sawStatus = false

        while (true) {
            when (reader.next()) {
                EventType.START_ELEMENT -> when {
                    reader.isDav("prop") -> {
                        val parsed = readProp(reader)
                        props = parsed.first
                        isCollection = parsed.second
                    }

                    reader.isDav("status") -> {
                        isOk = readText(reader).contains("200")
                        sawStatus = true
                    }

                    else -> skipElement(reader)
                }

                EventType.END_ELEMENT, EventType.END_DOCUMENT -> break
                else -> Unit
            }
        }

        return PropStat(props, isCollection, if (sawStatus) isOk else true)
    }

    /** 位置：`<prop>` 的 START_ELEMENT；返回属性表与「是否集合」。 */
    private fun readProp(reader: XmlReader): Pair<Map<String, String>, Boolean> {
        val props = mutableMapOf<String, String>()
        var isCollection = false

        while (true) {
            when (reader.next()) {
                EventType.START_ELEMENT -> {
                    if (reader.localName == "resourcetype") {
                        isCollection = readResourceType(reader)
                    } else {
                        props[reader.localName] = readText(reader)
                    }
                }

                EventType.END_ELEMENT, EventType.END_DOCUMENT -> break
                else -> Unit
            }
        }

        return props to isCollection
    }

    /** 位置：`<resourcetype>` 的 START_ELEMENT；目录包含 `<collection/>` 子元素。 */
    private fun readResourceType(reader: XmlReader): Boolean {
        var isCollection = false

        while (true) {
            when (reader.next()) {
                EventType.START_ELEMENT -> {
                    if (reader.localName == "collection") isCollection = true
                    skipElement(reader)
                }

                EventType.END_ELEMENT, EventType.END_DOCUMENT -> return isCollection
                else -> Unit
            }
        }
    }

    /** 位置：叶子元素的 START_ELEMENT；返回它的文本并消费到 END_ELEMENT。 */
    private fun readText(reader: XmlReader): String {
        val builder = StringBuilder()

        while (true) {
            when (reader.next()) {
                EventType.TEXT, EventType.CDSECT, EventType.IGNORABLE_WHITESPACE ->
                    builder.append(reader.text)

                EventType.START_ELEMENT -> skipElement(reader)
                EventType.END_ELEMENT, EventType.END_DOCUMENT -> return builder.toString().trim()
                else -> Unit
            }
        }
    }

    /** 位置：任意元素的 START_ELEMENT；完整消费它（空元素同样匹配）。 */
    private fun skipElement(reader: XmlReader) {
        var level = 1
        while (level > 0) {
            when (reader.next()) {
                EventType.START_ELEMENT -> level++
                EventType.END_ELEMENT -> level--
                EventType.END_DOCUMENT -> return
                else -> Unit
            }
        }
    }

    /**
     * 命名空间容错：严格按 `DAV:` 匹配，同时接受未声明命名空间的同名元素（少数服务器如此）。
     */
    private fun XmlReader.isDav(localName: String): Boolean =
        this.localName == localName &&
            (namespaceURI == DAV_NAMESPACE || namespaceURI.isEmpty())

    /** href 可能是绝对地址或编码路径，统一为解码后的服务器绝对路径。 */
    private fun toPath(href: String): String {
        val raw = href.trim()
        val isAbsolute = raw.startsWith("http://", ignoreCase = true) ||
            raw.startsWith("https://", ignoreCase = true)
        val withoutAuthority = if (isAbsolute) {
            val rest = raw.substringAfter("://")
            val pathStart = rest.indexOf('/')
            if (pathStart >= 0) rest.substring(pathStart) else "/"
        } else {
            raw
        }
        val decoded = runCatching { withoutAuthority.decodeURLPart() }.getOrDefault(withoutAuthority)
        return if (decoded.startsWith('/')) decoded else "/$decoded"
    }
}

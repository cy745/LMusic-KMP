package com.lalilu.lmedia.source.webdav

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.encodeURLPath
import io.ktor.http.isSuccess
import org.koin.core.annotation.Single

/** WebDAV 操作的最小抽象：真实实现走 HTTP，测试用假实现替代网络。 */
interface WebDavClient {
    /** `Depth: 1` 列出目录，返回结果包含目录自身。 */
    suspend fun propfind(path: String, depth: Int = 1): List<WebDavEntry>

    /** 读取小文件（元数据 json、封面）；不存在时返回 null。 */
    suspend fun download(path: String): ByteArray?

    /** 写入小文件；目标目录不存在时由调用方保证。 */
    suspend fun put(path: String, bytes: ByteArray, contentType: String)

    fun close()
}

/** 生产实现由 Koin 提供；测试注入假实现即可脱离网络验证扫描与提交流程。 */
fun interface WebDavClientFactory {
    fun create(config: WebDavConfig): WebDavClient
}

@Single(binds = [WebDavClientFactory::class])
class HttpWebDavClientFactory : WebDavClientFactory {
    override fun create(config: WebDavConfig): WebDavClient = HttpWebDavClient(config)
}

/** 面向用户的失败原因，UI 直接展示 [message]。 */
sealed class WebDavException(message: String) : IllegalStateException(message) {
    class Unauthorized(message: String) : WebDavException(message)
    class Forbidden(message: String) : WebDavException(message)
    class NotFound(message: String) : WebDavException(message)
    class Unexpected(message: String) : WebDavException(message)
}

class HttpWebDavClient(
    private val config: WebDavConfig,
    private val client: HttpClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
        }
    },
) : WebDavClient {
    private val baseUrl = normalizeServerUrl(config.url)

    private fun hasCredentials(): Boolean =
        config.username.isNotBlank() || config.password.isNotBlank()

    override suspend fun propfind(path: String, depth: Int): List<WebDavEntry> {
        val response = client.request(urlOf(path)) {
            method = HttpMethod("PROPFIND")
            header("Depth", depth.toString())
            if (hasCredentials()) basicAuth(config.username, config.password)
            // 显式构造内容，避免依赖调用方是否安装默认转换器。
            setBody(TextContent(PROPFIND_QUERY, ContentType.Application.Xml))
        }
        ensureSuccess(response)
        val body = response.bodyAsText()
        // 反代或网盘网关有时用 200 返回 HTML 错误页；解析失败必须显式报错，
        // 否则会被上层当成"这个目录没有内容"，进而用空结果覆盖媒体库。
        return try {
            WebDavXml.parseMultiStatus(body)
        } catch (throwable: Throwable) {
            throw WebDavException.Unexpected(
                "服务器返回的内容不是有效的 WebDAV 响应（${response.status.value}）"
            )
        }
    }

    override suspend fun download(path: String): ByteArray? {
        val response = client.get(urlOf(path)) {
            if (hasCredentials()) basicAuth(config.username, config.password)
        }
        if (response.status == HttpStatusCode.NotFound) return null
        ensureSuccess(response)
        return response.readRawBytes()
    }

    override suspend fun put(path: String, bytes: ByteArray, contentType: String) {
        val response = client.put(urlOf(path)) {
            if (hasCredentials()) basicAuth(config.username, config.password)
            setBody(ByteArrayContent(bytes, ContentType.parse(contentType)))
        }
        ensureSuccess(response)
    }

    override fun close() {
        client.close()
    }

    /** 拼接请求地址；路径按 URL path 规则编码（保留分隔符，转义空格与中文）。 */
    private fun urlOf(path: String): String {
        val normalized = if (path.startsWith('/')) path else "/$path"
        return baseUrl + normalized.encodeURLPath()
    }

    private fun ensureSuccess(response: HttpResponse) {
        if (response.status.isSuccess()) return
        throw when (response.status.value) {
            401 -> WebDavException.Unauthorized("认证失败：请检查用户名与应用专用密码")
            403 -> WebDavException.Forbidden("没有访问权限：请检查该账号对目标目录的权限")
            404 -> WebDavException.NotFound("路径不存在：请检查库根目录设置")
            else -> WebDavException.Unexpected("WebDAV 请求失败：${response.status.description}")
        }
    }

    companion object {
        /** 只请求真正用得到的属性，避免 allprop 在大目录上产生更长的响应。 */
        internal val PROPFIND_QUERY = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop>
                <d:resourcetype/>
                <d:getetag/>
                <d:getcontentlength/>
                <d:getlastmodified/>
                <d:getcontenttype/>
              </d:prop>
            </d:propfind>
        """.trimIndent()
    }
}

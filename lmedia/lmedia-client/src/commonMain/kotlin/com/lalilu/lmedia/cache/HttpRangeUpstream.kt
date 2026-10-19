package com.lalilu.lmedia.cache

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.readByteArray
import kotlin.time.TimeSource

/**
 * 代理访问上游所需的全部信息。
 *
 * 通用层只认识"一个 http(s) 地址 + 一组请求头"：认证、签名、Cookie 都在 [headers] 里，
 * 于是播放地址本身不含任何凭据，任何在线数据源都不用为缓存写代码。
 *
 * @property url 原始媒体地址（含协议与主机）
 * @property headers 访问该地址所需的请求头（认证等）
 * @property totalSize 远端文件总长度；0 表示未知，此时只能整文件透传缓存
 * @property contentType 媒体类型提示，为空时由字节自行嗅探
 */
data class CachedOrigin(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val totalSize: Long = 0L,
    val contentType: String? = null,
)

/**
 * 上游返回了非 2xx 状态码。
 *
 * 单独成类型是为了让代理能把"远端文件已经不在了（404）"和"网络/服务端故障"区分开，
 * 回给播放器一个明确的状态码，而不是先回 206 再截断。
 */
internal class UpstreamStatusException(
    val statusCode: Int,
    url: String,
) : IllegalStateException("上游返回 $statusCode：$url")

/**
 * 用普通 HTTP Range 请求访问上游。
 *
 * 与具体数据源无关：WebDAV、Subsonic、任意 CDN 都是"带 Range 头的 GET"。缓存键到
 * [CachedOrigin] 的映射由调用方先注册（见 [MediaCacheController.register]），之后代理
 * 的每个请求都从这里取上游描述。
 */
private val upstreamLogger = Logger.withTag("RangeUpstream")

internal class HttpRangeUpstream(
    private val http: HttpClient = defaultHttpClient(),
) : RangeUpstream {

    private val mutex = Mutex()
    private val origins = mutableMapOf<String, CachedOrigin>()

    /** 注册（或更新）某个缓存键的上游；重复注册直接覆盖，用于地址/签名刷新。 */
    suspend fun register(key: String, origin: CachedOrigin) {
        mutex.withLock { origins[key] = origin }
    }

    suspend fun clear() {
        mutex.withLock { origins.clear() }
    }

    /** 释放连接池；代理停止时调用，避免测试里留下线程。 */
    fun close() {
        http.close()
    }

    override suspend fun resolve(key: String): UpstreamTarget? = mutex.withLock {
        // token 直接复用缓存键：代理只把它原样传回来，不用再维护第二套标识
        origins[key]?.let { origin ->
            UpstreamTarget(
                token = key,
                totalSize = origin.totalSize,
                contentType = origin.contentType,
            )
        }
    }

    override suspend fun fetch(
        target: UpstreamTarget,
        start: Long,
        endInclusive: Long?,
        onChunk: suspend (ByteArray) -> Unit,
    ) {
        val origin = mutex.withLock { origins[target.token] }
            ?: throw IllegalStateException("缓存键没有对应的上游：${target.token}")

        val startedAt = TimeSource.Monotonic.markNow()
        upstreamLogger.i { "上游请求 ${target.token} bytes=$start-${endInclusive ?: ""} → ${origin.url}" }
        http.prepareGet(origin.url) {
            origin.headers.forEach { (name, value) -> header(name, value) }
            header(HttpHeaders.Range, "bytes=$start-${endInclusive ?: ""}")
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw UpstreamStatusException(response.status.value, origin.url)
            }
            // 从中间开始却拿到 200：上游忽略了 Range，字节其实是从 0 开始的。
            // 照单全收会把"文件开头"写进缓存中间位置，整份缓存从此错位。
            if (start > 0L && response.status != HttpStatusCode.PartialContent) {
                throw IllegalStateException("上游不支持 Range：${origin.url}")
            }
            // 回了 206 但起点不是我们要的：同样会把字节写错位置，直接丢弃这次响应。
            val contentRangeStart = RangeHeader.startFromContentRange(response.headers[HttpHeaders.ContentRange])
            if (contentRangeStart != null && contentRangeStart != start) {
                throw IllegalStateException(
                    "上游返回了错误的区间起点（请求 $start，实际 $contentRangeStart）：${origin.url}"
                )
            }
            val channel = response.bodyAsChannel()
            var received = 0L
            while (true) {
                val buffer = channel.readBuffer(MediaCacheStore.CHUNK_SIZE)
                if (buffer.size == 0L) break
                received += buffer.size
                onChunk(buffer.readByteArray())
            }
            upstreamLogger.i {
                "上游完成 ${target.token} 状态=${response.status.value} 收到 ${received}B " +
                    "用时 ${startedAt.elapsedNow().inWholeMilliseconds}ms"
            }
        }
    }

    private companion object {
        /**
         * 下载整首可能要几分钟，默认的请求超时会把长连接掐断；这里只约束"连不上"的时间，
         * 传输本身靠代理侧的读超时来兜底。
         */
        fun defaultHttpClient(): HttpClient = HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 60_000
            }
        }
    }
}

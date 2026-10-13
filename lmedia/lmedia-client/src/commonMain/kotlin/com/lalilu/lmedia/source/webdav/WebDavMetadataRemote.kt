package com.lalilu.lmedia.source.webdav

/**
 * 元数据的远端存放：WebDAV 上的一个目录，结构与本地缓存一致（`<key>.json` + `<key>.cover`）。
 *
 * 只在"同步到 WebDAV"开关打开时装配。所有写操作都可能在只读目录上失败，调用方必须优雅处理。
 */
internal interface WebDavMetadataRemote {
    /** 确保根目录与封面目录存在（已存在视为成功）；不可写时抛 [WebDavException]。 */
    suspend fun ensureReady()

    suspend fun read(key: String): ByteArray?
    suspend fun readCover(key: String): ByteArray?
    suspend fun write(key: String, bytes: ByteArray)
    suspend fun writeCover(key: String, bytes: ByteArray)

    /** 远端已有的元数据键，用于"只上传缺的那几条"。 */
    suspend fun keys(): Set<String>
}

/**
 * 走 WebDAV 的远端实现。
 *
 * 目录结构固定成与本地一致，所以"上传"就是把本地那几份文件搬到远端；读取同理。
 * [rootPath] 允许用户指到自己的目录（默认 `/.lmusic/`），不要求它是库根目录的子目录。
 *
 * **封面与 json 平级存放**（`<key>.cover`），不建子目录：不少服务器不支持 `MKCOL`
 * （实测 bytemark/webdav 对不存在的目录也返回 405），一旦需要建子目录就会每次上传都失败。
 * 只要求根目录可用，是能覆盖最多服务器的形态。
 */
internal class HttpWebDavMetadataRemote(
    private val client: WebDavClient,
    rootPath: String,
    private val rootFallback: String,
) : WebDavMetadataRemote {

    private val root: String = normalizeDirectoryPath(rootPath.ifBlank { rootFallback })

    override suspend fun ensureReady() {
        check(client.mkcol(root)) {
            "同步目录不可用（$root）：服务器不允许创建目录，请先手动创建该目录或换一个已有目录"
        }
    }

    override suspend fun read(key: String): ByteArray? = client.download(metaPath(key))

    override suspend fun readCover(key: String): ByteArray? = client.download(coverPath(key))

    override suspend fun write(key: String, bytes: ByteArray) {
        client.put(metaPath(key), bytes, CONTENT_TYPE_JSON)
    }

    override suspend fun writeCover(key: String, bytes: ByteArray) {
        client.put(coverPath(key), bytes, CONTENT_TYPE_BINARY)
    }

    /**
     * 列出远端已有元数据的键。
     *
     * 目录还不存在时返回空集合而不是报错：开关刚打开、远端还没建目录是正常状态，
     * 真正的写失败会在 [ensureReady] / [write] 上被抓到并提示。
     */
    override suspend fun keys(): Set<String> = runCatching {
        client.propfind(root, depth = 1)
            .asSequence()
            .filterNot { it.isDirectory }
            .map { it.path }
            .filter { it.endsWith(META_SUFFIX) }
            .map { it.substringAfterLast('/').removeSuffix(META_SUFFIX) }
            .toSet()
    }.getOrDefault(emptySet())

    private fun metaPath(key: String): String = joinPath(root, "$key$META_SUFFIX")

    private fun coverPath(key: String): String = joinPath(root, "$key.cover")

    private companion object {
        const val META_SUFFIX = ".json"
        const val CONTENT_TYPE_JSON = "application/json"
        const val CONTENT_TYPE_BINARY = "application/octet-stream"

        fun joinPath(parent: String, child: String): String =
            if (parent.endsWith('/')) parent + child else "$parent/$child"
    }
}

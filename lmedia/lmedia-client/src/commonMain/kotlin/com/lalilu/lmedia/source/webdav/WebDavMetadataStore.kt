package com.lalilu.lmedia.source.webdav

import com.lalilu.common.ext.md5
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.Json

/** 边车文件（同目录的封面图 / 同名 .lrc）在本地缓存里的分类。 */
internal enum class WebDavSidecarKind(internal val suffix: String) {
    COVER("cover"),
    LYRIC("lyric"),
}

/** 元数据远端同步的对外状态，供源卡片展示。 */
sealed interface WebDavMetadataSyncState {
    /** 未开启同步，或上一次同步顺利结束。 */
    data object Idle : WebDavMetadataSyncState

    data object Syncing : WebDavMetadataSyncState

    data class Synced(val uploaded: Int, val pulled: Int) : WebDavMetadataSyncState

    /**
     * [readOnly] 区分"目标目录只读/无权限"与其它失败：前者要提示用户换目录或改权限，
     * 后者多半是网络问题，重试即可。
     */
    data class Failed(val message: String, val readOnly: Boolean = false) : WebDavMetadataSyncState
}

/**
 * 提取结果的缓存：`<key>.json` 保存元数据，`<key>.cover` 保存内嵌封面原始字节。
 *
 * 放在缓存目录而不是应用数据目录：这部分内容可以重新提取（或从远端同步取回），不应该进入
 * 系统备份。
 *
 * 读取是**三层**的：内存 → 本地文件 → 远端（仅在装配了 [remote] 时）。远端命中的记录会回写本地，
 * 因此网络只在小范围内付出一次代价；本地命中时完全不碰网络。同一条记录两边都有时取
 * [WebDavMetadataRecord.extractedAt] 更新的一方（"新者胜"），避免用旧数据覆盖新提取结果。
 */
internal class WebDavMetadataStore(
    cacheRoot: String,
    private val json: Json,
    /**
     * 远端层用提供者而不是实例：开关可以随时切换，而"同步到 WebDAV"是可选项，
     * 重建 store 会丢掉内存层并让已装配的提取器指向旧对象。
     */
    private val remoteProvider: () -> WebDavMetadataRemote? = { null },
    /** 远端写失败（只读目录、网络抖动）只上报，不影响本地写入与提取流程。 */
    private val onRemoteError: (Throwable) -> Unit = {},
) {
    private val metaDirectory: Path
    private val coverDirectory: Path
    private val sidecarDirectory: Path

    /** 一层：进程内缓存，避免同一首歌在提取/进度统计里反复读文件与网络。 */
    private val memory = mutableMapOf<String, WebDavMetadataRecord>()

    init {
        val root = Path(cacheRoot, "lmedia", "webdav")
        metaDirectory = Path(root, "meta")
        coverDirectory = Path(root, "covers")
        sidecarDirectory = Path(root, "sidecars")
    }

    fun ensureReady() {
        SystemFileSystem.createDirectories(metaDirectory, mustCreate = false)
        SystemFileSystem.createDirectories(coverDirectory, mustCreate = false)
        SystemFileSystem.createDirectories(sidecarDirectory, mustCreate = false)
    }

    /** 本地已有记录的键（用于把历史记录补传到远端）。 */
    fun localKeys(): Set<String> =
        runCatching {
            SystemFileSystem.list(metaDirectory)
                .map { it.name }
                .filter { it.endsWith(META_SUFFIX) }
                .map { it.removeSuffix(META_SUFFIX) }
                .toSet()
        }.getOrDefault(emptySet())

    suspend fun read(key: String): WebDavMetadataRecord? {
        memory[key]?.let { return it }

        val local = readLocal(key)
        val remoteText = remoteProvider()?.let { runCatching { it.read(key) }.getOrNull() }
        val remoteRecord = remoteText?.let(::decode)
        val chosen = when {
            local == null -> remoteRecord
            remoteRecord == null -> local
            // 两边都有：新者胜；完全相同也走这条分支（不产生多余写）
            remoteRecord.extractedAt > local.extractedAt -> remoteRecord
            else -> local
        } ?: return null

        // 远端更新（或本地缺失）时把结果落回本地：下次就是纯本地命中
        if (chosen !== local) {
            writeLocal(key, chosen)
            // 只有远端记录声称有封面时才去拉：否则每次读都会白跑一趟远端 GET
            if (chosen.hasCover) writeCoverFromRemote(key)
        }
        memory[key] = chosen
        return chosen
    }

    /**
     * 是否已有可用于当前指纹的提取记录。[requireComplete] 为真时只有完整提取的记录才算数
     * （例如判断"这首歌还需要后台补下来吗"）。
     *
     * [consultRemote] 默认关闭：批量遍历（进度统计）不该产生每首歌一次的网络请求。
     * 需要"远端已有元数据就别重复下载音频"的场景显式打开，代价只会付一次（命中后回写本地）。
     */
    suspend fun has(
        key: String,
        fingerprint: String,
        requireComplete: Boolean = false,
        consultRemote: Boolean = false,
    ): Boolean {
        val record = if (consultRemote) read(key) else memory[key] ?: readLocal(key)
        if (record == null) return false
        if (record.fingerprint != fingerprint) return false
        return !requireComplete || record.complete
    }

    /** 写入本地与内存，并在装配了远端时尽力同步过去。 */
    suspend fun write(key: String, record: WebDavMetadataRecord, cover: ByteArray?) {
        writeLocal(key, record)
        memory[key] = record
        if (cover != null && cover.isNotEmpty()) writeLocalCover(key, cover)

        val activeRemote = remoteProvider() ?: return
        val payload = json.encodeToString(record).encodeToByteArray()
        runCatching { activeRemote.write(key, payload) }
            .onFailure(onRemoteError)
            .onSuccess {
                if (cover != null && cover.isNotEmpty()) {
                    runCatching { activeRemote.writeCover(key, cover) }.onFailure(onRemoteError)
                }
            }
    }

    /** 把本地已有的一条记录补传到远端（开关打开时把历史记录补齐）。 */
    suspend fun upload(key: String): Boolean {
        val record = readLocal(key) ?: return false
        val activeRemote = remoteProvider() ?: return false
        val payload = json.encodeToString(record).encodeToByteArray()
        val written = runCatching { activeRemote.write(key, payload) }
            .onFailure(onRemoteError)
            .isSuccess
        if (!written) return false
        readCover(key)?.let { cover ->
            runCatching { activeRemote.writeCover(key, cover) }.onFailure(onRemoteError)
        }
        return true
    }

    fun readCover(key: String): ByteArray? {
        val path = coverPath(key)
        if (!SystemFileSystem.exists(path)) return null
        return runCatching {
            SystemFileSystem.source(path).buffered().use { it.readByteArray() }
        }.getOrNull()?.takeIf(ByteArray::isNotEmpty)
    }

    fun delete(key: String) {
        listOf(metaPath(key), coverPath(key)).forEach { path ->
            if (SystemFileSystem.exists(path)) SystemFileSystem.delete(path)
        }
        memory.remove(key)
        WebDavSidecarKind.entries.forEach { kind ->
            sidecarPaths(key, kind).forEach { path ->
                if (SystemFileSystem.exists(path)) SystemFileSystem.delete(path)
            }
        }
    }

    /** 把远端已有但本地缺失的记录拉回本地（新设备或缓存被清理后的第一次访问）。 */
    suspend fun pullFromRemote(keys: Collection<String>): Int {
        val activeRemote = remoteProvider() ?: return 0
        var pulled = 0
        keys.forEach { key ->
            if (readLocal(key) != null) return@forEach
            val record = runCatching { activeRemote.read(key) }.getOrNull()?.let(::decode)
                ?: return@forEach
            writeLocal(key, record)
            memory[key] = record
            writeCoverFromRemote(key)
            pulled += 1
        }
        return pulled
    }

    // ── 边车文件（同目录封面 / 同名 .lrc）──

    /**
     * 读取边车文件，未命中时拉取并缓存。
     *
     * 缓存文件名带 [fingerprint]：远端换了封面/歌词（etag 改变）时会重新拉取，
     * 旧文件留给缓存淘汰处理，不会读到过期内容。
     */
    suspend fun sidecar(
        key: String,
        kind: WebDavSidecarKind,
        fingerprint: String?,
        fetch: suspend () -> ByteArray?,
    ): ByteArray? {
        val path = sidecarPath(key, kind, fingerprint)
        readFile(path)?.let { return it }
        val bytes = runCatching { fetch() }.getOrNull()?.takeIf(ByteArray::isNotEmpty) ?: return null
        ensureReady()
        runCatching {
            SystemFileSystem.sink(path).buffered().use { it.write(bytes) }
        }
        return bytes
    }

    private fun readLocal(key: String): WebDavMetadataRecord? = readFile(metaPath(key))?.let(::decode)

    private fun decode(bytes: ByteArray): WebDavMetadataRecord? =
        runCatching { json.decodeFromString<WebDavMetadataRecord>(bytes.decodeToString()) }.getOrNull()

    private fun readFile(path: Path): ByteArray? {
        if (!SystemFileSystem.exists(path)) return null
        return runCatching {
            SystemFileSystem.source(path).buffered().use { it.readByteArray() }
        }.getOrNull()
    }

    private fun writeLocal(key: String, record: WebDavMetadataRecord) {
        ensureReady()
        val text = json.encodeToString(record)
        SystemFileSystem.sink(metaPath(key)).buffered().use { it.writeString(text) }
    }

    private fun writeLocalCover(key: String, cover: ByteArray) {
        ensureReady()
        SystemFileSystem.sink(coverPath(key)).buffered().use { it.write(cover) }
    }

    private suspend fun writeCoverFromRemote(key: String) {
        if (readCover(key) != null) return
        val bytes = runCatching { remoteProvider()?.readCover(key) }.getOrNull() ?: return
        if (bytes.isEmpty()) return
        runCatching { writeLocalCover(key, bytes) }
    }

    private fun metaPath(key: String): Path = Path(metaDirectory, "$key$META_SUFFIX")

    private fun coverPath(key: String): Path = Path(coverDirectory, "$key.cover")

    /**
     * 边车缓存文件名把指纹取哈希而不是原样拼接：etag 常带引号与斜杠（`"tag-1/2"`），
     * 原样进文件名在 Windows 上直接写不进去（引号是非法字符），结果是每次都重新下载。
     */
    private fun sidecarPath(key: String, kind: WebDavSidecarKind, fingerprint: String?): Path =
        Path(sidecarDirectory, "$key.${kind.suffix}.${fingerprint?.md5() ?: "raw"}")

    private fun sidecarPaths(key: String, kind: WebDavSidecarKind): List<Path> =
        runCatching {
            SystemFileSystem.list(sidecarDirectory)
                .filter { it.name.startsWith("$key.${kind.suffix}.") }
        }.getOrDefault(emptyList())

    private companion object {
        const val META_SUFFIX = ".json"
    }
}

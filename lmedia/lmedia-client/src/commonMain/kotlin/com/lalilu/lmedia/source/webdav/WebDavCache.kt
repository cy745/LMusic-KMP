package com.lalilu.lmedia.source.webdav

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/**
 * 音频字节的本地缓存。
 *
 * 采用**只追加的前缀模型**：`<key>.part` 里始终是从 0 开始的一段连续前缀，已缓存长度就等于文件
 * 长度本身。这样做的好处是不需要随机写，而且进程被杀掉也不会留下"记了却没写进去"的假覆盖——
 * 文件实际长度就是唯一真相。
 *
 * 代价：向前跳转播放时，要把中间没缓存的那段一起下完（单曲大小有界，且这段数据本来也会被缓存）。
 */
internal class WebDavCache(cacheRoot: String) {

    companion object {
        internal const val CHUNK_SIZE = 64 * 1024
        private const val AUDIO_DIR = "audio"
        private const val AUDIO_SUFFIX = ".part"
    }

    private val root: Path = Path(cacheRoot, "lmedia", "webdav")
    private val audioDirectory: Path = Path(root, AUDIO_DIR)

    fun ensureReady() {
        SystemFileSystem.createDirectories(audioDirectory, mustCreate = false)
    }

    /** 已缓存的前缀长度。 */
    fun filledSize(key: String): Long =
        SystemFileSystem.metadataOrNull(audioPath(key))?.size ?: 0L

    /** 打开从 [start] 起始的读取源；文件不存在时返回 null。 */
    fun openSource(key: String, start: Long): Source? {
        val path = audioPath(key)
        if (!SystemFileSystem.exists(path)) return null
        val source = SystemFileSystem.source(path).buffered()
        if (start > 0L) source.skip(start)
        return source
    }

    /** 以追加方式打开写入端，调用方负责 flush 与 close。目录不存在时自动创建。 */
    fun openAppendSink(key: String): Sink {
        ensureReady()
        return SystemFileSystem.sink(audioPath(key), append = true).buffered()
    }

    fun appendBytes(key: String, bytes: ByteArray) {
        openAppendSink(key).use { sink -> sink.write(bytes) }
    }

    /** 读取一段字节，用于校验与后续的标签解析。 */
    fun readBytes(key: String, start: Long, length: Int): ByteArray? {
        if (length <= 0) return ByteArray(0)
        val source = openSource(key, start) ?: return null
        return source.use { it.readByteArray(length) }
    }

    /** 交给标签解析使用的本地路径；文件不存在或为空时返回 null。 */
    fun localPath(key: String): String? {
        val path = audioPath(key)
        if (!SystemFileSystem.exists(path)) return null
        if ((SystemFileSystem.metadataOrNull(path)?.size ?: 0L) <= 0L) return null
        return path.toString()
    }

    fun delete(key: String) {
        val path = audioPath(key)
        if (SystemFileSystem.exists(path)) SystemFileSystem.delete(path)
    }

    /** 已缓存的所有缓存键。 */
    fun keys(): List<String> =
        runCatching {
            SystemFileSystem.list(audioDirectory)
                .map { it.name }
                .filter { it.endsWith(AUDIO_SUFFIX) }
                .map { it.removeSuffix(AUDIO_SUFFIX) }
        }.getOrDefault(emptyList())

    fun usedBytes(): Long = keys().sumOf(::filledSize)

    private fun audioPath(key: String): Path = Path(audioDirectory, "$key$AUDIO_SUFFIX")
}

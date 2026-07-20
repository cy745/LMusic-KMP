package com.lalilu.lmedia.task

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.isDirectory
import io.github.vinceglb.filekit.list
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 文件扫描任务。
 *
 * 使用 **Worker 拉取** 模式遍历目录树：一旦扫描到子目录就立刻入队，
 * 空闲 Worker 立即消费，无需等待整层扫描完毕，吞吐量更高。
 *
 * @param predicate      过滤条件，返回 true 表示文件被选中
 * @param maxConcurrency 最大并发 I/O 数，控制同时打开的 fd 数量
 */
class FileScannerTask(
    private val predicate: suspend (PlatformFile) -> Boolean,
    private val maxConcurrency: Int = 64,
) {

    /**
     * 执行扫描。
     *
     * @param root 要扫描的根目录
     * @return 匹配 [predicate] 的文件集合
     */
    suspend fun scan(root: PlatformFile): Collection<PlatformFile> = withContext(Dispatchers.IO) {
        if (!root.isDirectory()) {
            return@withContext if (predicate(root)) listOf(root) else emptyList()
        }

        val directory = mutableSetOf(root)
        val list = mutableSetOf<PlatformFile>()
        val semaphore = Semaphore(maxConcurrency)

        while (isActive && directory.isNotEmpty()) {
            val files = directory.flatMap { it.list() }
            val results = files
                .map { async { semaphore.withPermit { Triple(it, it.isDirectory(), predicate(it)) } } }
                .awaitAll()

            directory.clear()
            for ((item, isDirectory, satisfy) in results) {
                if (isDirectory) directory.add(item)
                if (satisfy) list.add(item)
            }
        }
        list.distinctBy { it.path }
    }
}

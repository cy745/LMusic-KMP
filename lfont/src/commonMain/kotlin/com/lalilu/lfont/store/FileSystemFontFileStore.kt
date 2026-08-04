/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lalilu.lfont.store

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.write

/**
 * 基于 [SystemFileSystem] 的应用私有目录实现。
 *
 * 根目录由各平台提供（Android filesDir / iOS Documents / JVM 用户目录），
 * 导入的字体文件持久化到该目录，避免依赖外部文件导致字体丢失。
 */
internal class FileSystemFontFileStore(
    private val root: Path,
) : FontFileStore {

    init {
        SystemFileSystem.createDirectories(root)
    }

    override suspend fun save(fileName: String, bytes: ByteArray): Result<Unit> = runCatching {
        SystemFileSystem.sink(Path(root, fileName))
            .buffered()
            .use { it.write(bytes) }
    }

    override suspend fun read(fileName: String): ByteArray? = runCatching {
        val file = Path(root, fileName)
        if (!SystemFileSystem.exists(file)) return@runCatching null
        SystemFileSystem.source(file)
            .buffered()
            .use { it.readByteArray() }
    }.getOrNull()

    override suspend fun delete(fileName: String): Result<Unit> = runCatching {
        val file = Path(root, fileName)
        if (SystemFileSystem.exists(file)) {
            SystemFileSystem.delete(file, mustExist = true)
        }
    }

    override suspend fun list(): List<Pair<String, Long>> = runCatching {
        buildList {
            for (file in SystemFileSystem.list(root)) {
                val size: Long = SystemFileSystem.metadataOrNull(file)?.size ?: 0L
                add(file.name to size)
            }
        }
    }.getOrDefault(emptyList())
}

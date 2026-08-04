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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.Path

actual fun createFontFileStore(): FontFileStore = InMemoryFontFileStore()

internal actual fun fontFileRoot(): Path = Path("lfont")

/**
 * wasm 端的内存兜底实现；IndexedDB 持久化在后续阶段接入。
 */
class InMemoryFontFileStore : FontFileStore {

    private val mutex = Mutex()
    private val files = LinkedHashMap<String, ByteArray>()

    override suspend fun save(fileName: String, bytes: ByteArray): Result<Unit> = mutex.withLock {
        runCatching { files[fileName] = bytes }
    }

    override suspend fun read(fileName: String): ByteArray? = mutex.withLock {
        files[fileName]
    }

    override suspend fun delete(fileName: String): Result<Unit> = mutex.withLock {
        runCatching { files.remove(fileName) }
    }

    override suspend fun list(): List<Pair<String, Long>> = mutex.withLock {
        files.map { it.key to it.value.size.toLong() }
    }
}

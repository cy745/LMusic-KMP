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

import kotlinx.io.files.Path

/**
 * 字体文件存储抽象：导入/下载的字体文件持久化到应用私有存储，
 * 避免依赖外部文件（如 content:// URI 或临时目录）导致字体丢失。
 *
 * 平台实现：
 * - Android/iOS/JVM：应用私有目录（FileKit.filesDir/lfont）
 * - wasm：内存兜底（IndexedDB 持久化在后续阶段实现）
 */
interface FontFileStore {
    /** 保存字体文件，覆盖同名文件。 */
    suspend fun save(fileName: String, bytes: ByteArray): Result<Unit>

    /** 读取字体文件字节，不存在时返回 null。 */
    suspend fun read(fileName: String): ByteArray?

    /** 删除字体文件。 */
    suspend fun delete(fileName: String): Result<Unit>

    /** 列出全部字体文件（fileName -> size）。 */
    suspend fun list(): List<Pair<String, Long>>
}

/** 创建当前平台的字体文件存储实现。 */
expect fun createFontFileStore(): FontFileStore

/** 字体文件存储根目录（应用私有目录），由各平台提供。 */
internal expect fun fontFileRoot(): Path

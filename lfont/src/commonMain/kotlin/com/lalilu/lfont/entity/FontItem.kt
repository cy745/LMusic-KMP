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

package com.lalilu.lfont.entity

import kotlinx.serialization.Serializable

/** 字体来源。 */
@Serializable
enum class FontSource {
    /** 应用附带字体。 */
    BUNDLED,

    /** 用户通过文件选择器导入的字体。 */
    IMPORTED,
}

/** 字体列表中的单个字体条目。 */
@Serializable
data class FontItem(
    /** 唯一标识（文件名或内容哈希），后续也作为字体加载的 identity。 */
    val id: String,
    /** 展示名（优先字体族名，当前阶段先用文件名）。 */
    val name: String,
    val source: FontSource,
    /** 存储文件名（含扩展名）。 */
    val fileName: String,
    val fileSize: Long,
    val appliedGlobal: Boolean = false,
    val appliedLyric: Boolean = false,
    val enabledByDefault: Boolean = false,
)

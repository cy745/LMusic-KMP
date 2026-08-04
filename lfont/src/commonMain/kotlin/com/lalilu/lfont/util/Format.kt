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

package com.lalilu.lfont.util

/** 将字节大小格式化为可读文本。 */
internal fun formatFileSize(size: Long): String {
    val mb = size / 1024.0 / 1024.0
    return when {
        mb >= 1.0 -> "${(mb * 10).toInt() / 10.0} MB"
        size >= 1024 -> "${size / 1024} KB"
        else -> "$size B"
    }
}

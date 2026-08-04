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

package com.lalilu.lfont.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.lalilu.lfont.entity.FontItem
import com.lalilu.lfont.entity.FontSource
import com.lalilu.lfont.store.FontFileStore
import com.lalilu.lfont.store.createFontFileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.Factory

/**
 * 字体列表状态与导入逻辑。
 */
@Factory
class FontsVM(
    private val store: FontFileStore = createFontFileStore()
) : ViewModel() {

    private val _fonts = MutableStateFlow<List<FontItem>>(emptyList())
    val fonts: StateFlow<List<FontItem>> = _fonts.asStateFlow()

    init {
        refresh()
    }

    /** 从存储重建字体列表。 */
    fun refresh() {
        viewModelScope.launch {
            _fonts.value = store.list().map { (fileName, size) ->
                FontItem(
                    id = fileName,
                    name = fileName.substringBeforeLast('.', fileName),
                    source = FontSource.IMPORTED,
                    fileName = fileName,
                    fileSize = size,
                )
            }
        }
    }

    /** 导入字体字节到应用私有存储，成功后刷新列表。 */
    fun importFont(bytes: ByteArray, fileName: String) {
        viewModelScope.launch {
            store.save(fileName, bytes)
                .onSuccess { refresh() }
                .onFailure {
                    Logger.e(
                        tag = "FontsVM",
                        messageString = "导入字体失败: $fileName",
                        throwable = it
                    )
                }
        }
    }
}

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

package com.lalilu.lfont.manager

import androidx.compose.ui.text.font.FontFamily
import co.touchlab.kermit.Logger
import com.lalilu.common.kv.KVContext
import com.lalilu.lfont.entity.FontItem
import com.lalilu.lfont.entity.FontSource
import com.lalilu.lfont.preview.loadFontFamily
import com.lalilu.lfont.store.FontFileStore
import com.lalilu.lfont.store.createFontFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

/** 字体应用状态：字体列表 + 已加载的全局/歌词字体。 */
data class AppFontState(
    val fonts: List<FontItem> = emptyList(),
    val globalFont: FontFamily? = null,
    val lyricFont: FontFamily? = null,
)

/** 字体元数据与勾选状态的 KV 存储。 */
object LFontKV : KVContext("lfont") {
    val items = obtainList("items", emptyList<FontItem>())
}

/**
 * 字体管理与应用入口：
 * 持有字体列表与全局/歌词字体状态，从 KV 恢复勾选状态并加载对应字体。
 */
@Single
class FontManager(
    private val store: FontFileStore = createFontFileStore(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(AppFontState())
    val state: StateFlow<AppFontState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** 从存储与 KV 重建字体列表，并加载已勾选的字体。 */
    fun refresh() {
        scope.launch {
            val saved = LFontKV.items.value
            val fonts = store.list().map { (fileName, size) ->
                val old = saved.firstOrNull { it.id == fileName }
                old?.copy(fileSize = size)
                    ?: FontItem(
                        id = fileName,
                        name = fileName.substringBeforeLast('.', fileName),
                        source = FontSource.IMPORTED,
                        fileName = fileName,
                        fileSize = size,
                    )
            }
            LFontKV.items.value = fonts
            _state.value = buildState(fonts)
        }
    }

    /** 导入字体字节到应用私有存储，成功后刷新列表。 */
    fun importFont(bytes: ByteArray, fileName: String) {
        scope.launch {
            store.save(fileName, bytes)
                .onSuccess { refresh() }
                .onFailure {
                    Logger.e(
                        tag = "FontManager",
                        messageString = "导入字体失败: $fileName",
                        throwable = it
                    )
                }
        }
    }

    /** 删除指定字体文件，被删除字体若正在应用则自动回退默认。 */
    fun deleteFonts(fileNames: List<String>) {
        scope.launch {
            fileNames.forEach { store.delete(it) }
            refresh()
        }
    }

    /**
     * 切换字体的全局/歌词应用状态，立即生效并持久化。
     *
     * 「界面」与「歌词」各自全局唯一：开启某项时自动清除其他字体的同项配置；
     * 同一字体可以同时配置界面与歌词。
     */
    fun setApplied(fileName: String, global: Boolean? = null, lyric: Boolean? = null) {
        scope.launch {
            val fonts = _state.value.fonts.map { font ->
                if (font.id == fileName) {
                    font.copy(
                        appliedGlobal = global ?: font.appliedGlobal,
                        appliedLyric = lyric ?: font.appliedLyric,
                    )
                } else {
                    font.copy(
                        appliedGlobal = if (global == true) false else font.appliedGlobal,
                        appliedLyric = if (lyric == true) false else font.appliedLyric,
                    )
                }
            }
            LFontKV.items.value = fonts
            _state.value = buildState(fonts)
        }
    }

    /** 由字体列表构建应用状态，并加载已勾选的字体。 */
    private fun buildState(fonts: List<FontItem>): AppFontState = AppFontState(
        fonts = fonts,
        globalFont = fonts.firstOrNull { it.appliedGlobal }?.let { loadFontFamily(it.fileName) },
        lyricFont = fonts.firstOrNull { it.appliedLyric }?.let { loadFontFamily(it.fileName) },
    )
}

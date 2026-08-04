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
import com.lalilu.extensions.ItemSelector
import com.lalilu.lfont.entity.FontItem
import com.lalilu.lfont.manager.FontManager
import org.koin.core.annotation.Factory

/**
 * 字体列表页状态：选择模式（多选）与批量删除。
 */
@Factory
class FontsScreenVM(
    val fontManager: FontManager,
) : ViewModel() {

    val selector = ItemSelector<FontItem>()

    /** 删除选中的字体并退出选择模式。 */
    fun deleteSelected() {
        val fileNames = selector.selected().map { it.fileName }
        selector.clear()
        fontManager.deleteFonts(fileNames)
    }
}

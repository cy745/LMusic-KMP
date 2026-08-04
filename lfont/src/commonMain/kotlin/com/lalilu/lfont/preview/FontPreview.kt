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

package com.lalilu.lfont.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/** 预览示例文本：覆盖中文、英文、数字、日文，长度保证跑马灯滚动。 */
const val FONT_PREVIEW_TEXT =
    "字体预览 Font Preview 0123 中文 日本語 ひらがな カタカナ 漢字 あいうえお アイウエオ 中文 English 数字"

/**
 * 加载指定字体的预览 [FontFamily]。
 *
 * Android 使用 [androidx.compose.ui.text.font.Font] 直接从私有目录文件加载；
 * iOS / wasm 的预览渲染随字体加载阶段统一接入，当前返回 null 回退默认字体。
 */
@Composable
expect fun rememberPreviewFont(fileName: String): FontFamily?

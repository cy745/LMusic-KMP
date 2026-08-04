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

package com.lalilu.lfont.component

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 字体预览跑马灯修饰符：
 * - 全宽贴边，左右 16dp 渐变淡出（与页面内容 padding 对齐）
 * - 文字初始从 16dp 处开始，滚动进入边缘时自然淡出
 */
fun Modifier.fontMarquee(background: Color): Modifier = this
    .fillMaxWidth()
    .drawWithContent {
        drawContent()
        val fadePx = 16.dp.toPx()
        val fadeFraction = (fadePx / size.width).toFloat()
        drawRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to background,
                    fadeFraction to Color.Transparent,
                    1f - fadeFraction to Color.Transparent,
                    1f to background
                )
            )
        )
    }
    .basicMarquee(iterations = Int.MAX_VALUE)
    .padding(start = 16.dp)

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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.lfont.entity.FontItem
import com.lalilu.lfont.entity.FontSource
import com.lalilu.lfont.preview.FONT_PREVIEW_TEXT
import com.lalilu.lfont.preview.rememberPreviewFont
import com.lalilu.lfont.util.formatFileSize
import com.lalilu.navigation.AppRouter

/** 字体来源对应的文案。 */
fun FontSource.label(): String = when (this) {
    FontSource.BUNDLED -> "预置"
    FontSource.IMPORTED -> "导入"
}

/**
 * 字体列表项：小号字体名称 + 跑马灯预览 + 淡化的来源与大小信息。
 */
@Composable
fun FontItemCard(
    item: FontItem,
    modifier: Modifier = Modifier,
) {
    val previewFont = rememberPreviewFont(item.fileName)
    val background = MaterialTheme.colorScheme.background

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                AppRouter.route("/settings/fonts/detail")
                    .with("fileName", item.fileName)
                    .jump()
            }
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = item.name,
            modifier = Modifier.padding(horizontal = 16.dp),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = FONT_PREVIEW_TEXT,
            modifier = Modifier.fontMarquee(background),
            fontFamily = previewFont ?: FontFamily.Default,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(1.dp))

        Text(
            text = "${item.source.label()} · ${formatFileSize(item.fileSize)}",
            modifier = Modifier.padding(horizontal = 16.dp),
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
        )
    }
}

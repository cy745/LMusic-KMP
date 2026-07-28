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

package com.lalilu.lhome.screen.detail

import androidx.compose.animation.animateBounds
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lalilu.adaptiveValue
import com.lalilu.animated
import com.lalilu.extensions.LocalToaster
import com.lalilu.preview.preview

@OptIn(ExperimentalGridApi::class)
@Composable
fun MetadataInfos(
    modifier: Modifier,
    metadata: Map<String, String>
) {
    val paddingHorizontal = adaptiveValue(
        compact = { 16.dp },
        medium = { 40.dp }
    ).animated()

    val column = adaptiveValue(
        compact = { 1 },
        medium = { 2 },
        expanded = { 3 }
    )

    val containerPadding = adaptiveValue(
        compact = { PaddingValues(top = 16.dp) },
        medium = {
            PaddingValues(
                top = 24.dp,
                start = paddingHorizontal.value,
                end = paddingHorizontal.value,
            )
        }
    )

    val itemContentPadding = adaptiveValue(
        compact = { 20.dp },
        medium = { 16.dp }
    )

    LookaheadScope lookaheadScope@{
        Grid(
            modifier = modifier.fillMaxWidth()
                .padding(containerPadding.value)
                .animateBounds(this@lookaheadScope),
            config = { repeat(column.value) { column(1.fr) } }
        ) {
            metadata.forEach { entry ->
                ColumnItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .gridItem(columnSpan = 1)
                        .animateBounds(this@lookaheadScope),
                    title = entry.key,
                    content = entry.value,
                    contentPadding = PaddingValues(
                        horizontal = itemContentPadding.value,
                        vertical = 8.dp
                    )
                )
            }
        }
    }
}

@Composable
private fun ColumnItem(
    modifier: Modifier = Modifier,
    title: String,
    content: String,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
) {
    val clipboard = LocalClipboard.current
    val toast = LocalToaster.current

    Column(
        modifier = modifier
            .combinedClickable(
                onLongClick = {
//                    clipboard.setClipEntry(ClipEntry(""))
//                    clipboard.setText(buildAnnotatedString { append(content) })
                    toast?.show("复制成功")
                },
                onClick = {
                    toast?.show("长按复制元素内容")
                }
            )
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            modifier = Modifier.alpha(0.5f),
            text = title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Preview
@Composable
private fun PreviewColumnItem() = preview {
    ColumnItem(
        content = "测试内容",
        title = "测试标题",
    )
}
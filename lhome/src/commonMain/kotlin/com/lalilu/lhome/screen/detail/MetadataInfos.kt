/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.RemixIcon
import com.lalilu.lfont.entity.FontItem
import com.lalilu.lfont.entity.FontSource
import com.lalilu.lfont.manager.FontManager
import com.lalilu.lfont.preview.FONT_PREVIEW_TEXT
import com.lalilu.lfont.preview.rememberPreviewFont
import com.lalilu.lfont.util.formatFileSize
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject

/** 字体来源对应的文案。 */
fun FontSource.label(): String = when (this) {
    FontSource.BUNDLED -> "预置"
    FontSource.IMPORTED -> "导入"
}

/**
 * 字体列表项：名称与来源信息两行 + 右侧设置入口，下方为跑马灯预览。
 *
 * 单击跳转详情（选择模式下为勾选）；长按进入选择模式，选中项以背景色标识。
 */
@Composable
fun FontItemCard(
    item: FontItem,
    modifier: Modifier = Modifier,
    isSelecting: () -> Boolean = { false },
    isSelected: () -> Boolean = { false },
    onEnterSelect: () -> Unit = {},
    onSelect: () -> Unit = {},
    onNavigate: () -> Unit = {},
) {
    val previewFont = rememberPreviewFont(item.fileName)
    val fontManager = koinInject<FontManager>()
    var configExpanded by remember { mutableStateOf(false) }

    val itemBackground by animateColorAsState(
        targetValue = when {
            isSelected() -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            isSelecting() -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)
            else -> Color.Transparent
        },
        label = "font_item_background"
    )
    val marqueeFadeColor =
        if (itemBackground == Color.Transparent) MaterialTheme.colorScheme.background
        else itemBackground

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(itemBackground)
            .combinedClickable(
                onClick = { if (isSelecting()) onSelect() else onNavigate() },
                onLongClick = { onEnterSelect() }
            )
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.name,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.height(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${item.source.label()} · ${formatFileSize(item.fileSize)}",
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                    )

                    AnimatedVisibility(
                        visible = item.appliedGlobal,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        ApplyTag(
                            text = "界面",
                            fontFamily = previewFont,
                            color = Color(0xFF1793FF),
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                    AnimatedVisibility(
                        visible = item.appliedLyric,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        ApplyTag(
                            text = "歌词",
                            fontFamily = previewFont,
                            color = Color(0xFF8BC34A),
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !isSelecting(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable { configExpanded = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            imageVector = vectorResource(RemixIcon.System.settings2Line),
                            contentDescription = "配置字体应用",
                            modifier = Modifier.size(16.dp),
                            colorFilter = ColorFilter.tint(
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                            )
                        )
                    }

                    DropdownMenu(
                        expanded = configExpanded,
                        onDismissRequest = { configExpanded = false },
                        modifier = Modifier.widthIn(min = 180.dp),
                        shape = RoundedCornerShape(12.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            ApplySwitchRow(
                                label = "界面",
                                checked = item.appliedGlobal,
                                onCheckedChange = {
                                    fontManager.setApplied(item.fileName, global = it)
                                }
                            )
                            ApplySwitchRow(
                                label = "歌词",
                                checked = item.appliedLyric,
                                onCheckedChange = {
                                    fontManager.setApplied(item.fileName, lyric = it)
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = FONT_PREVIEW_TEXT,
            modifier = Modifier.fontMarquee(marqueeFadeColor),
            fontFamily = previewFont ?: FontFamily.Default,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/** 应用状态胶囊标签。 */
@Composable
private fun ApplyTag(
    text: String,
    fontFamily: FontFamily?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        fontSize = 9.sp,
        lineHeight = 12.sp,
        fontFamily = fontFamily ?: FontFamily.Default,
        color = color
    )
}

/** 配置菜单中的应用开关行（样式与播放模式下拉一致）。 */
@Composable
private fun ApplySwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(min = 180.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

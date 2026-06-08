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

package com.lalilu.lsettings.component.preferences

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lalilu.RemixIcon
import com.lalilu.common.settings.DropdownPreference
import com.lalilu.remixicon.Arrows
import com.lalilu.remixicon.System
import com.lalilu.remixicon.arrows.arrowDownSLine
import com.lalilu.remixicon.system.checkLine


/**
 * 单选下拉偏好项的 Material3 行实现（**Popup 形式**）。
 *
 * ## 视觉约定（与项目内其他列表行统一）
 *
 * - `Row.padding(horizontal=16dp, vertical=12dp)`，无 Card 外壳
 * - 标题 `bodyLarge` + `onBackground`
 * - 副标题 `bodySmall` 12sp + `onBackground.copy(0.6f)`
 * - 尾部是带箭头的"触发器"：当前值 + 下拉箭头
 * - 行内 `Arrangement.spacedBy(12.dp)`
 *
 * ## 行为：Popup 而非 Dialog
 *
 * - 点击行（或点击尾部触发器）→ 弹出 [DropdownMenu]，**就地**展开在触发器下方
 * - 不会蒙灰背景 / 不会抢占焦点外的全部交互
 * - 选项 [DropdownPreference.options] 直接铺在 popup 内
 * - 选中项**右侧**加 `checkLine` 图标作为状态指示（text 居左、✓ 居右）
 * - 选中后立即关闭 popup 并写值
 * - 点击 popup 外部 / 按返回键 自动关闭
 *
 * ## 视觉定制（与项目整体一致）
 *
 * - **宽度**：`widthIn(min = 180.dp)`，避免文字过短时 popup 过窄
 * - **圆角**：`RoundedCornerShape(12.dp)`，与其他圆角控件统一
 * - **颜色**：使用 `surfaceContainerLow` 作为容器色，比 `surface` 略提亮，但保持扁平
 * - **阴影**：`tonalElevation = 0.dp` + `shadowElevation = 0.dp`，**无投影**
 * - **描边**：1dp `onSurface.copy(0.12f)`，代替阴影给出"边界感"
 *
 * ## 关键实现：Box 锚点
 *
 * 触发器与 [DropdownMenu] 必须放在同一个 `Box` 内。
 * `DropdownMenu` 的定位依赖其**直接父级** `Box` 的布局坐标；
 * 若作为外层 `Row` 的兄弟节点，它会回退到上层的 LazyColumn item 坐标，
 * 导致 popup 距离 row 很远（实测约 170dp）。
 *
 * 与全屏 [com.lalilu.lsettings.dialog.ListSelectDialog] 的差异：
 * 适合"少量选项 + 快速切换"场景（典型 2~7 个）。选项很多时建议仍用 Dialog。
 *
 * 测试 tag：
 * - 行：`preference_dropdown_<key>`
 * - 触发器：`preference_dropdown_<key>_trigger`
 * - 弹出菜单：`preference_dropdown_<key>_menu`
 * - 每个选项：`preference_dropdown_<key>_option_<index>`
 */
@Composable
fun <T : Any> DropdownPreferenceRow(
    pref: DropdownPreference<T>,
    modifier: Modifier = Modifier
) {
    val isEnabled = pref.enabled()
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("preference_dropdown_${pref.key}")
            .clickable(enabled = isEnabled) { expanded = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pref.title(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            pref.summary?.invoke()?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        // 触发器 + 弹出菜单：必须用 Box 包起来作为锚点
        // 这样 DropdownMenu 会以 trigger 自身作为定位基准，紧贴 trigger 下方弹出
        Box {
            Row(
                modifier = Modifier
                    .testTag("preference_dropdown_${pref.key}_trigger")
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = pref.optionLabel(pref.value),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = RemixIcon.Arrows.arrowDownSLine,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .testTag("preference_dropdown_${pref.key}_menu")
                    // 最小宽度 180dp，避免文字过短（如"是"）时 popup 过窄
                    .widthIn(min = 180.dp),
                // 圆角加大到 12dp，与项目内其他圆角控件统一
                shape = RoundedCornerShape(12.dp),
                // 静态容器色：与 surface 区分开（用 surfaceContainerLow 给一点对比），
                // 但不依赖 elevation 抬色 —— 保持扁平
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                // 阴影 / 色调阴影都关掉，popup 走"纯描边 + 静态背景"路线
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                // 1dp 描边，alpha 0.12 的 onSurface —— 在浅色背景下给出明确的边缘
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                ),
            ) {
                pref.options.forEachIndexed { index, option ->
                    val isSelected = option == pref.value
                    DropdownMenuItem(
                        modifier = Modifier
                            .testTag("preference_dropdown_${pref.key}_option_$index"),
                        text = {
                            Text(
                                text = pref.optionLabel(option),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        },
                        // 选中图标放右侧：未选中时用 Spacer 占位保持文本对齐
                        trailingIcon = {
                            if (isSelected) {
                                Icon(
                                    imageVector = RemixIcon.System.checkLine,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                // 占位：保持菜单项文本对齐
                                Spacer(modifier = Modifier.size(18.dp))
                            }
                        },
                        onClick = {
                            expanded = false
                            pref.onValueChange(option)
                        }
                    )
                }

                // 选项较多时滚动指示（>6 项时下边线暗示）
                if (pref.options.size > 6) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                }
            }
        }
    }
}

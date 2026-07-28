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

package com.lalilu.lsettings.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp


/**
 * 单选 / 多选共用的 ListSelect 弹层。
 *
 * - [multiSelect] = false：[selected] 是单个 [T]，点击项直接通过 [onConfirm] 回调
 * - [multiSelect] = true： [selected] 是 [Set]<[T]>，可多选后点"确认"回调
 *
 * 取消按钮触发 [onDismiss]（不调 [onConfirm]）。
 *
 * 测试 tag：
 * - 单选模式：外层 `list_select_dialog_single`
 * - 多选模式：外层 `list_select_dialog_multi`
 */
@Composable
fun <T : Any> ListSelectDialog(
    title: String,
    options: List<T>,
    optionLabel: (T) -> String,
    selected: T,
    multiSelect: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Any?) -> Unit,
) {
    if (multiSelect) {
        MultiSelectListSelectDialog(
            title = title,
            options = options,
            optionLabel = optionLabel,
            initial = emptySet(),
            onDismiss = onDismiss,
            onConfirm = onConfirm
        )
    } else {
        SingleSelectListDialog(
            title = title,
            options = options,
            optionLabel = optionLabel,
            selected = selected,
            onDismiss = onDismiss,
            onConfirm = onConfirm
        )
    }
}


/** 兼容多选入参为 [Set] 的便捷重载。 */
@Composable
fun <T : Any> ListSelectDialog(
    title: String,
    options: List<T>,
    optionLabel: (T) -> String,
    selected: Set<T>,
    multiSelect: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (Any?) -> Unit,
) {
    MultiSelectListSelectDialog(
        title = title,
        options = options,
        optionLabel = optionLabel,
        initial = selected,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}


@Composable
private fun <T : Any> SingleSelectListDialog(
    title: String,
    options: List<T>,
    optionLabel: (T) -> String,
    selected: T,
    onDismiss: () -> Unit,
    onConfirm: (Any?) -> Unit,
) {
    var current by remember { mutableStateOf(selected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .testTag("list_select_dialog_single"),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(options, key = { optionLabel(it) }) { option ->
                    val isSelected = current == option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                current = option
                                onConfirm(option)
                            }
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                current = option
                                onConfirm(option)
                            }
                        )
                        Text(
                            text = optionLabel(option),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}


@Composable
private fun <T : Any> MultiSelectListSelectDialog(
    title: String,
    options: List<T>,
    optionLabel: (T) -> String,
    initial: Set<T>,
    onDismiss: () -> Unit,
    onConfirm: (Any?) -> Unit,
) {
    val state: SnapshotStateList<T> = remember {
        initial.toMutableStateList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .testTag("list_select_dialog_multi")
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(options, key = { optionLabel(it) }) { option ->
                        val checked = option in state
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) state.remove(option) else state.add(option)
                                }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) state.add(option) else state.remove(option)
                                }
                            )
                            Text(
                                text = optionLabel(option),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.toSet()) }) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

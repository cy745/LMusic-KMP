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

package com.lalilu.navigation.smartbar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lalilu.extensions.DialogItem
import com.lalilu.extensions.DialogWrapper
import com.lalilu.navigation.ActionContext
import com.lalilu.navigation.ScreenAction


/**
 * 更多操作面板对话框
 */
@Composable
internal fun MoreActionPanelDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    actions: List<ScreenAction>,
) {
    if (!isVisible) return
    val dialog = remember {
        DialogItem.Dynamic(backgroundColor = Color.Transparent) {
            MoreActionPanelDialogContent(
                actions = actions,
                onDismiss = { dismiss() }
            )
        }
    }

    DialogWrapper.register(
        isVisible = { isVisible },
        onDismiss = onDismiss,
        dialogItem = dialog
    )
}

/**
 * 更多操作面板对话框内容
 */
@Composable
private fun MoreActionPanelDialogContent(
    modifier: Modifier = Modifier,
    actions: List<ScreenAction>,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(0.1f)),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp)),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            actions.forEach { action ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                ) {
                    ActionItem(
                        action = action,
                        actionContext = ActionContext(
                            isFullyExpanded = true,
                            onDismiss = onDismiss
                        )
                    )
                }
            }
        }
    }
}

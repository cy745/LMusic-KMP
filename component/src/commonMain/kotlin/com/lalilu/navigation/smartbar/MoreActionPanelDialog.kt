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

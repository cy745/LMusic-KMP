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

package com.lalilu.lmusic.screen.smartbar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        MoreActionPanelDialogContent(
            actions = actions,
            onDismiss = onDismiss
        )
    }
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
            .padding(bottom = 8.dp)
            .navigationBarsPadding(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(0.1f)),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 10.dp,
        color = MaterialTheme.colorScheme.surface
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
                        actionContext = ActionContext(isFullyExpanded = true)
                    )
                }
            }
        }
    }
}

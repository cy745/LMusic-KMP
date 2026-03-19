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

package com.lalilu.lhome.screen.songs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.lalilu.RemixIcon
import com.lalilu.navigation.ActionContext
import com.lalilu.navigation.ScreenAction
import com.lalilu.navigation.ScreenBarFactory
import com.lalilu.navigation.smartbar.BackActionBtn
import com.lalilu.navigation.smartbar.NavigateCommonBarContent
import com.lalilu.remixicon.System
import com.lalilu.remixicon.system.closeLine


@Composable
fun ScreenBarFactory.SongsSelectorPanel(
    isVisible: () -> Boolean,
    onDismiss: () -> Unit,
    screenActions: List<ScreenAction>? = null,
) {
    RegisterContent(
        isVisible = isVisible,
        onDismiss = onDismiss,
        onBackPressed = { }
    ) {
        SongsSelectorPanelContent(
            modifier = Modifier,
            screenActions = screenActions,
            onBackPress = { onDismiss() }
        )
    }
}

@Composable
private fun SongsSelectorPanelContent(
    modifier: Modifier = Modifier,
    screenActions: List<ScreenAction>?,
    onBackPress: (() -> Unit)? = null
) {
    val dialogVisible = remember { mutableStateOf(false) }

    NavigateCommonBarContent(
        modifier = modifier,
        backActionBtn = BackActionBtn(
            text = "取消",
            icon = RemixIcon.System.closeLine
        ),
        dialogVisible = dialogVisible.value,
        screenActions = screenActions,
        actionContext = ActionContext(false),
        onBackPress = onBackPress,
        onDialogVisibilityChange = { dialogVisible.value = it }
    )
}

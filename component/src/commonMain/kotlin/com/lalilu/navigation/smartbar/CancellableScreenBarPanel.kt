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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.lalilu.RemixIcon
import com.lalilu.navigation.ActionContext
import com.lalilu.navigation.ScreenAction
import com.lalilu.navigation.ScreenBarFactory
import com.lalilu.remixicon.System
import com.lalilu.remixicon.system.closeLine

@Composable
fun ScreenBarFactory.CancellableScreenBarPanel(
    isVisible: () -> Boolean,
    onDismiss: () -> Unit,
    screenActions: List<ScreenAction>? = null,
) {
    RegisterContent(
        isVisible = isVisible,
        onDismiss = onDismiss,
        onBackPressed = { }
    ) {
        CancellableScreenBarPanelContent(
            modifier = Modifier,
            screenActions = screenActions,
            onBackPress = { onDismiss() }
        )
    }
}

@Composable
private fun CancellableScreenBarPanelContent(
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

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

package com.lalilu.lmusic.window

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import java.awt.Toolkit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FrameWindowScope.MacOsWindowFrame(
    state: WindowState,
    onCloseRequest: () -> Unit = {},
    content: @Composable (windowInset: WindowInsets, captionBarInset: WindowInsets) -> Unit
) {
    val windowInset = remember(state) {
        derivedStateOf {
            if (state.placement == WindowPlacement.Fullscreen) WindowInsets(0)
            else {
                val windowInsets = Toolkit.getDefaultToolkit().getScreenInsets(window.graphicsConfiguration)
                WindowInsets(windowInsets.left, windowInsets.top, windowInsets.right, windowInsets.bottom)
            }
        }
    }

//    LaunchedEffect(window, captionBarHeight) {
//        window.findSkiaLayer()?.disableTitleBar(captionBarHeight.value)
//    }

    content(windowInset.value, WindowInsets(0))

    window.rootPane.apply {
        rootPane.putClientProperty("apple.awt.fullWindowContent", true)
        rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
        rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
    }
}
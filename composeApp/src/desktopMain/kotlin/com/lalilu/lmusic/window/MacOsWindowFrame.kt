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

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FrameWindowScope.MacOsWindowFrame(
    state: WindowState,
    onCloseRequest: () -> Unit = {},
    content: @Composable (windowInset: WindowInsets, captionBarInset: WindowInsets) -> Unit
) {
    val density = LocalDensity.current
    val windowInset = remember(state) {
        derivedStateOf {
            if (state.placement == WindowPlacement.Fullscreen) WindowInsets(0)
            else WindowInsets(0, density.run { 27.dp.roundToPx() }, 0, 0)
        }
    }


    Box {
        content(windowInset.value, WindowInsets(0))

        Spacer(
            Modifier.align(Alignment.TopCenter)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        state.placement = if (state.placement == WindowPlacement.Maximized) WindowPlacement.Floating
                        else WindowPlacement.Maximized
                    })
                }
                .layout { measurable, constraints ->
                    val statusBarHeight = windowInset.value.getTop(density)
                    val placeable = measurable.measure(
                        constraints.copy(
                            maxHeight = statusBarHeight,
                            minHeight = statusBarHeight
                        )
                    )

                    layout(placeable.measuredWidth, placeable.measuredHeight) {
                        placeable.place(0, 0)
                    }
                }
        )
    }

    window.rootPane.apply {
        rootPane.putClientProperty("apple.awt.fullWindowContent", true)
        rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
        rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
    }
}
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
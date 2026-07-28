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

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.zIndex
import com.lalilu.lmusic.jna.windows.ComposeWindowProcedure
import com.lalilu.lmusic.jna.windows.structure.WinUserConst.HTCAPTION
import com.lalilu.lmusic.jna.windows.structure.WinUserConst.HTCLIENT
import com.lalilu.lmusic.jna.windows.structure.WinUserConst.HTCLOSE
import com.lalilu.lmusic.jna.windows.structure.WinUserConst.HTMAXBUTTON
import com.lalilu.lmusic.jna.windows.structure.WinUserConst.HTMINBUTTON
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import java.awt.Window

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FrameWindowScope.WindowsWindowFrame(
    state: WindowState,
    onCloseRequest: () -> Unit = {},
    content: @Composable (windowInset: WindowInsets, captionBarInset: WindowInsets) -> Unit
) {
    val paddingInset = remember { mutableStateOf(WindowInsets(0)) }
    val maxButtonRect = remember { mutableStateOf(Rect.Zero) }
    val minButtonRect = remember { mutableStateOf(Rect.Zero) }
    val closeButtonRect = remember { mutableStateOf(Rect.Zero) }
    val captionBarRect = remember { mutableStateOf(Rect.Zero) }
    val layoutHitTestOwner = rememberLayoutHitTestOwner()
    val contentPaddingInset = remember { MutableWindowInsets() }
    val procedure = remember(window) {
        ComposeWindowProcedure(
            window = window,
            hitTest = { x, y ->
                when {
                    maxButtonRect.value.contains(Offset(x, y)) -> HTMAXBUTTON
                    minButtonRect.value.contains(Offset(x, y)) -> HTMINBUTTON
                    closeButtonRect.value.contains(Offset(x, y)) -> HTCLOSE
                    captionBarRect.value.contains(Offset(x, y)) && !layoutHitTestOwner.hitTest(x, y) -> HTCAPTION

                    else -> HTCLIENT
                }
            },
            onWindowInsetUpdate = { }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        content(paddingInset.value, contentPaddingInset)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .onGloballyPositioned {
                    captionBarRect.value = it.boundsInWindow()
                    paddingInset.value = WindowInsets(top = captionBarRect.value.height.toInt())
                }
        ) {
            Spacer(Modifier.weight(1f))

            window.CaptionButtonRow(
                windowHandle = procedure.windowHandle,
                isMaximize = state.placement == WindowPlacement.Maximized,
                onCloseRequest = onCloseRequest,
                onMaximizeButtonRectUpdate = { maxButtonRect.value = it },
                onMinimizeButtonRectUpdate = { minButtonRect.value = it },
                onCloseButtonRectUpdate = { closeButtonRect.value = it },
                accentColor = procedure.windowFrameColor,
                frameColorEnabled = procedure.isWindowFrameAccentColorEnabled,
                isActive = procedure.isWindowActive,
                modifier = Modifier.align(Alignment.Top)
                    .onSizeChanged {
                        contentPaddingInset.insets = WindowInsets(right = it.width, top = it.height)
                    }
            )
        }
    }
}

@Composable
fun Window.CaptionButtonRow(
    windowHandle: WinDef.HWND,
    isMaximize: Boolean,
    isActive: Boolean,
    accentColor: Color,
    frameColorEnabled: Boolean,
    onCloseRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onMaximizeButtonRectUpdate: (Rect) -> Unit,
    onMinimizeButtonRectUpdate: (Rect) -> Unit = {},
    onCloseButtonRectUpdate: (Rect) -> Unit = {}
) {
    Row(
        modifier = modifier
            .zIndex(1f)
    ) {
        TextButton(
            modifier = Modifier.onGloballyPositioned { onMinimizeButtonRectUpdate(it.boundsInWindow()) },
            onClick = { User32.INSTANCE.ShowWindow(windowHandle, WinUser.SW_MINIMIZE) },
            content = {
                Text(text = "Minimize")
            }
        )
        TextButton(
            modifier = Modifier.onGloballyPositioned { onMaximizeButtonRectUpdate(it.boundsInWindow()) },
            onClick = {
                User32.INSTANCE.ShowWindow(
                    windowHandle,
                    if (isMaximize) WinUser.SW_RESTORE else WinUser.SW_MAXIMIZE
                )
            },
            content = {
                Text(text = if (isMaximize) "Restore" else "Maximize")
            }
        )
        TextButton(
            modifier = Modifier.onGloballyPositioned { onCloseButtonRectUpdate(it.boundsInWindow()) },
            onClick = { onCloseRequest() },
            content = {
                Text(text = "Close")
            }
        )
    }
}

fun Rect.contains(x: Float, y: Float): Boolean {
    return x in left..<right && y >= top && y < bottom
}
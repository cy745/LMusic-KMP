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

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState
import com.lalilu.lmusic.jna.windows.structure.isWindows10OrLater
import org.jetbrains.skiko.hostOs

@Composable
fun FrameWindowScope.WindowFrame(
    state: WindowState,
    onCloseRequest: () -> Unit = {},
    content: @Composable (windowInset: WindowInsets, captionBarInset: WindowInsets) -> Unit
) {
    when {
        hostOs.isWindows && isWindows10OrLater() -> {
            WindowsWindowFrame(
                state = state,
                onCloseRequest = onCloseRequest,
                content = content
            )
        }

        hostOs.isMacOS -> {
            MacOsWindowFrame(
                state = state,
                onCloseRequest = onCloseRequest,
                content = content
            )
        }

        else -> {
            content(WindowInsets(0), WindowInsets(0))
        }
    }
}
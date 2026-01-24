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
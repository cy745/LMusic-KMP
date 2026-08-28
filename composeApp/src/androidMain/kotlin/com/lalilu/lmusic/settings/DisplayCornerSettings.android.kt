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

package com.lalilu.lmusic.settings

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView

@Composable
internal actual fun rememberSystemDisplayCornerRadii(): SystemDisplayCornerRadii? {
    val view = LocalView.current
    val density = LocalDensity.current.density
    var radii by remember(view, density) {
        mutableStateOf(readDisplayCornerRadii(view, density))
    }

    // 不接管 ComposeView 的 WindowInsets listener，避免破坏 Compose 自己的 Insets 分发。
    // 圆角在窗口布局完成后是稳定值；监听布局足以覆盖首次挂载、旋转和窗口尺寸变化。
    DisposableEffect(view, density) {
        val refresh = Runnable {
            radii = readDisplayCornerRadii(view, density)
        }
        val listener = View.OnLayoutChangeListener { target, _, _, _, _, _, _, _, _ ->
            radii = readDisplayCornerRadii(target, density)
        }
        view.addOnLayoutChangeListener(listener)
        view.post(refresh)
        onDispose {
            view.removeCallbacks(refresh)
            view.removeOnLayoutChangeListener(listener)
        }
    }

    return radii
}

private fun readDisplayCornerRadii(
    view: View,
    density: Float,
): SystemDisplayCornerRadii? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || density <= 0f) return null
    val insets = view.rootWindowInsets ?: return null

    fun radius(position: Int): Float =
        (insets.getRoundedCorner(position)?.radius ?: 0) / density

    return SystemDisplayCornerRadii(
        topLeftDp = radius(RoundedCorner.POSITION_TOP_LEFT),
        topRightDp = radius(RoundedCorner.POSITION_TOP_RIGHT),
    )
}

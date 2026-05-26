package com.lalilu.lmusic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.lalilu.common.kv.KVContext
import kotlinx.serialization.Serializable

@Serializable
data class WindowStateItem(
    var placement: WindowPlacement = WindowPlacement.Floating,
    var isMinimized: Boolean = false,
    var x: Float = -1f,
    var y: Float = -1f,
    var w: Float = 800f,
    var h: Float = 800f,
)

object WindowStateKeeper {
    private var state by KVContext
        .obtainStatic<WindowStateItem>("window-state", WindowStateItem())

    @Composable
    fun rememberWindowState(): WindowState {
        val windowPosition = remember {
            state.run {
                if (x == -1f || y == -1f) {
                    return@run WindowPosition.PlatformDefault
                }
                WindowPosition(x.dp, y.dp)
            }
        }

        return androidx.compose.ui.window.rememberWindowState(
            placement = state.placement,
            isMinimized = false,
            position = windowPosition,
            size = DpSize(state.w.dp, state.h.dp)
        )
    }

    fun saveWindowState(windowState: WindowState) {
        state = WindowStateItem(
            placement = windowState.placement,
            isMinimized = windowState.isMinimized,
            x = windowState.position.x.value,
            y = windowState.position.y.value,
            w = windowState.size.width.value,
            h = windowState.size.height.value,
        )
    }
}
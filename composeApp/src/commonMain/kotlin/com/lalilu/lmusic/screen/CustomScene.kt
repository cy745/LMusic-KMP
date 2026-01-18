package com.lalilu.lmusic.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.lalilu.navigation.Metadata
import com.lalilu.navigation.Screen

class CustomScene<T : Screen>(
    val home: NavEntry<T>,
    val player: NavEntry<T>,
) : Scene<T> {
    override val key: Any = "CustomScene"
    override val entries: List<NavEntry<T>> = listOf(home, player)
    override val previousEntries: List<NavEntry<T>> = emptyList()

    override val content: @Composable (() -> Unit) = {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.5f)
            ) {
                home.Content()
            }
        }
    }
}

class CustomSceneStrategy<T : Screen>(
    private val windowSizeClass: WindowSizeClass
) : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        // 检查屏幕宽度，如果小于中低分辨率，则返回null
        if (!windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)) {
            return null
        }

        // 快速判断，减少不必要的计算
        if (entries.size != 2) return null

        val home = entries.firstOrNull { it.metadata[Metadata.KEY_IS_HOME] == true } ?: return null
        val player = entries.firstOrNull { it.metadata[Metadata.KEY_IS_PLAYER] == true } ?: return null

        return CustomScene(home, player)
    }
}

@Composable
fun <T : Screen> rememberCustomSceneStrategy(): CustomSceneStrategy<T> {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    return remember(windowSizeClass) {
        CustomSceneStrategy(windowSizeClass)
    }
}
package com.lalilu.navigation.smartbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import com.lalilu.navigation.*

object SmartbarStackHolder {
    val stackMap = mutableStateMapOf<String, Pair<ScreenBarComponent?, List<ScreenAction>?>>()

    @Composable
    fun Wrap(screen: Screen) {
        screen.Content()

        val screenItem = screen.actualScreen()
        val barComponent = (screenItem as? ScreenBarFactory)?.content()
        val actions = (screenItem as? ScreenActionFactory)?.provideScreenActions()
        stackMap[screenItem.key] = barComponent to actions

        DisposableEffect(Unit) {
            onDispose {
                stackMap.remove(screenItem.key)
                if (screenItem is ScreenBarFactory) {
                    ComponentStack.removeInstance(screenItem)
                }
            }
        }
    }
}
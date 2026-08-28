package com.lalilu.navigation.smartbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import com.lalilu.navigation.*

object SmartbarStackHolder {
    val stackMap = mutableStateMapOf<String, Pair<ScreenBarComponent?, List<ScreenAction>?>>()

    @Composable
    fun Wrap(screen: Screen) {
        screen.Content()

        val screenItem = screen.actualScreen()
        val barComponent = (screenItem as? ScreenBarFactory)?.content()
        val providedActions = (screenItem as? ScreenActionFactory)?.provideScreenActions()
        val smartBarActions = providedActions?.filterNot { it is ScreenAction.DeepLink }
        val deepLinkActions = providedActions.orEmpty().filterIsInstance<ScreenAction.DeepLink>()
        val registration = remember(screen) {
            ScreenActionRegistry.createRegistration(screen)
        }

        SideEffect {
            ScreenActionRegistry.update(registration, deepLinkActions)
        }
        stackMap[screenItem.key] = barComponent to smartBarActions

        DisposableEffect(registration) {
            onDispose {
                ScreenActionRegistry.unregister(registration)
                stackMap.remove(screenItem.key)
                if (screenItem is ScreenBarFactory) {
                    ComponentStack.removeInstance(screenItem)
                }
            }
        }
    }
}

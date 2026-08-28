package com.lalilu.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * 保存仍处于组合中的 [ScreenAction.DeepLink]。
 *
 * 注册项使用导航栈中的 Screen 实例做引用匹配，避免两个同类型详情页在转场期间因为共享
 * [Screen.key] 而互相覆盖。注册表不保留已退出组合页面的回调。
 */
object ScreenActionRegistry {
    class Registration internal constructor(internal val screen: Screen)

    private data class Entry(
        val registration: Registration,
        val actions: List<ScreenAction.DeepLink>,
    )

    private val entries = MutableStateFlow<List<Entry>>(emptyList())

    fun createRegistration(screen: Screen): Registration = Registration(screen)

    fun update(registration: Registration, actions: List<ScreenAction.DeepLink>) {
        entries.update { current ->
            current.filterNot { it.registration === registration } + Entry(registration, actions)
        }
    }

    fun unregister(registration: Registration) {
        entries.update { current ->
            current.filterNot { it.registration === registration }
        }
    }

    /** 等待指定导航条目完成组合并注册；空列表同样表示注册已经完成。 */
    suspend fun await(screen: Screen): List<ScreenAction.DeepLink> = entries
        .map { current ->
            current.lastOrNull { it.registration.screen === screen }?.actions
        }
        .filterNotNull()
        .first()
}

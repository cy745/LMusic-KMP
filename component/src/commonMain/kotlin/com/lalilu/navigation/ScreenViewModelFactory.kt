package com.lalilu.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel

/**
 * 由 [Screen] 组合实现的 ViewModel 获取能力。
 *
 * 同一个页面的 Content、ScreenAction、ScreenBar 等组件都应通过 [vm] 获取 ViewModel，
 * 以集中管理路由参数、Koin parameters、key 和 qualifier，避免不同调用点各自声明依赖。
 */
interface ScreenViewModelFactory<T : ViewModel> {
    @Composable
    fun vm(): T
}

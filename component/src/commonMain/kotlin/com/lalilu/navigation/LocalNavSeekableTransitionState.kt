package com.lalilu.navigation

import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.scene.Scene

/**
 * 用于提供导航过渡动画状态的组合局部变量
 * 允许组件控制或监听导航场景间的过渡进度
 */
val LocalNavSeekableTransitionState =
    staticCompositionLocalOf<State<SeekableTransitionState<Scene<Screen>>?>> { error("Not provided") }
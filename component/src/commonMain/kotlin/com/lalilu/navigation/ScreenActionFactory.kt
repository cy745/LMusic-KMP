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

package com.lalilu.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.DrawableResource


/**
 * Screen action 的上下文信息
 */
@Stable
@Immutable
data class ActionContext(
    val isFullyExpanded: Boolean = false,
    val onDismiss: () -> Unit = {}
)


/**
 * Screen 操作项的密封类
 */
@Stable
sealed class ScreenAction {

    /**
     * 静态操作项，具有固定的标题、图标、颜色等属性
     */
    @Stable
    data class Static(
        val title: @Composable () -> String,
        val subTitle: @Composable () -> String? = { null },
        val color: @Composable () -> Color = { Color.Unspecified },
        val icon: @Composable () -> DrawableResource? = { null },
        val dotColor: @Composable () -> Color? = { null },
        val longClick: (ActionContext) -> Boolean = { false },
        val onAction: (ActionContext) -> Unit = {}
    ) : ScreenAction()

    /**
     * 动态操作项，内容由 composable 函数自定义
     */
    @Stable
    data class Dynamic(
        val content: @Composable (ActionContext) -> Unit
    ) : ScreenAction()

    /**
     * 仅供 `lmusic://screen_action/<key>` 触发的隐藏操作，不参与 SmartBar 渲染。
     *
     * 实例由当前页面的 [ScreenActionFactory.provideScreenActions] 在组合期间提供，因此回调捕获的是
     * 当前导航条目真实使用的 ViewModel。页面不在栈顶或已经退出组合时不会执行。
     */
    @Stable
    data class DeepLink(
        val key: String,
        val onAction: (ActionContext) -> Unit = {},
    ) : ScreenAction()
}


/**
 * 接口定义了提供屏幕操作项的工厂方法
 */
interface ScreenActionFactory {

    /**
     * 提供屏幕操作列表
     *
     * @return 返回 [ScreenAction] 列表
     */
    @Composable
    fun provideScreenActions(): List<ScreenAction> {
        return emptyList()
    }
}

/** 为现有可见操作复用同一个回调，额外声明一个不会显示在 SmartBar 中的 Deep Link 入口。 */
fun ScreenAction.Static.deepLink(key: String): ScreenAction.DeepLink =
    ScreenAction.DeepLink(key = key, onAction = onAction)

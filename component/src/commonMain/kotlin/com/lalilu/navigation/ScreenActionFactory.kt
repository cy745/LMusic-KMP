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

package com.lalilu.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector


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
        val icon: @Composable () -> ImageVector? = { null },
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

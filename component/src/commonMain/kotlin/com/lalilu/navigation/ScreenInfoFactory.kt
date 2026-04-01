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
import androidx.compose.ui.graphics.vector.ImageVector


/**
 * 数据类用于表示屏幕的基本信息。
 *
 * @property title 屏幕标题，通过可组合函数提供动态标题内容。
 * @property icon 屏幕图标，可选参数，默认为 null。
 */
data class ScreenInfo(
    val title: @Composable () -> String,
    val icon: ImageVector? = null
)

/**
 * 接口定义了提供屏幕信息的工厂方法。
 */
interface ScreenInfoFactory {

    fun isTabScreen(): Boolean = false

    /**
     * 提供屏幕信息的可组合函数。
     *
     * @return 返回包含屏幕标题和图标的 [ScreenInfo] 对象。
     */
    @Composable
    fun provideScreenInfo(): ScreenInfo
}
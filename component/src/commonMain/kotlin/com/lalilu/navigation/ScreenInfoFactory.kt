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
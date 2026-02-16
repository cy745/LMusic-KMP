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

package com.lalilu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.window.core.layout.WindowSizeClass

/**
 * 屏幕尺寸模式枚举
 *
 * 用于区分不同的屏幕尺寸类型，以适配不同的布局策略
 */
enum class ScreenMode {
    /** 手机等小屏设备 */
    Phone,

    /** 平板等大屏设备 */
    Tablet,

    /** 未知状态（窗口尺寸尚未确定） */
    Unknown
}

@Composable
fun currentScreenMode(): ScreenMode {
    val currentWindowSize = currentWindowSize()
    // 宽高任一为0，则认为处于Unknown模式
    if (currentWindowSize.width == 0 || currentWindowSize.height == 0)
        return ScreenMode.Unknown

    val windowClass = currentWindowAdaptiveInfo().windowSizeClass
    val widthLargerThanMedium = windowClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val heightLargerThanMedium = windowClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

    return when {
        // 两条边都不为Compat，则认为处于平板模式
        widthLargerThanMedium && heightLargerThanMedium -> ScreenMode.Tablet
        else -> ScreenMode.Phone
    }
}

/**
 * 屏幕模式处理器
 *
 * 确保应用在获取到正确的窗口尺寸后才开始显示 UI。
 *
 * 在窗口尺寸确定之前（如窗口初始化或调整大小时），screenMode 会处于 Unknown 状态，
 * 此时通过 AnimatedVisibility 隐藏内容，避免出现错误的布局闪烁。
 * 当尺寸确定后（宽高都不为 0），才会淡入显示实际的 UI 内容。
 *
 * 使用方式：
 * ```
 * ScreenModeHandler {
 *     // 你的实际 UI 内容
 *     MainScreen()
 * }
 * ```
 *
 * @param content 需要根据屏幕模式显示的 UI 内容
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ScreenModeHandler(content: @Composable () -> Unit = {}) {
    val screenMode: ScreenMode = currentScreenMode()

    // 只有当screenMode不为Unknown时，才显示实际内容
    AnimatedVisibility(
        modifier = Modifier.fillMaxSize(),
        visible = screenMode != ScreenMode.Unknown,
        enter = fadeIn(),
        exit = fadeOut(),
        content = { content() }
    )
}

/**
 * 判断窗口宽度是否至少达到中等尺寸阈值
 *
 * @return 如果窗口宽度 >= WIDTH_DP_MEDIUM_LOWER_BOUND 返回 true，否则返回 false
 */
fun WindowSizeClass.atLeastMedium(): Boolean {
    return isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
}

/**
 * 判断窗口宽度是否至少达到展开尺寸阈值
 *
 * @return 如果窗口宽度 >= WIDTH_DP_EXPANDED_LOWER_BOUND 返回 true，否则返回 false
 */
fun WindowSizeClass.atLeastExpanded(): Boolean {
    return isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
}

/**
 * 根据当前窗口尺寸自适应地应用不同的 Modifier
 *
 * 提供三种尺寸模式的 Modifier 应用逻辑：
 * - compact: 小屏设备（默认）
 * - medium: 中等屏幕设备
 * - expanded: 大屏设备
 *
 * @param compact 小屏设备下的 Modifier 构建器（必填）
 * @param medium 中等屏幕设备下的 Modifier 构建器（可选，默认使用 compact）
 * @param expanded 大屏设备下的 Modifier 构建器（可选，默认使用 medium）
 * @return 根据当前窗口尺寸应用相应 Modifier 后的新 Modifier
 */
fun Modifier.adaptive(
    compact: @Composable Modifier.() -> Modifier,
    medium: @Composable (Modifier.() -> Modifier)? = null,
    expanded: @Composable (Modifier.() -> Modifier)? = medium
) = composed {
    val windowClass = currentWindowAdaptiveInfo().windowSizeClass

    then(
        when {
            windowClass.atLeastMedium() -> medium?.invoke(this) ?: compact()
            windowClass.atLeastExpanded() -> expanded?.invoke(this) ?: compact()
            else -> compact()
        }
    )
}

/**
 * 根据当前窗口尺寸自适应地提供不同类型的值
 *
 * 适用于需要根据不同屏幕尺寸返回不同类型数据的场景
 *
 * @param T 值的类型参数
 * @param compact 小屏设备下的值提供器（必填）
 * @param medium 中等屏幕设备下的值提供器（可选，默认使用 compact）
 * @param expanded 大屏设备下的值提供器（可选，默认使用 medium）
 * @return 包含当前适配值的 State 对象
 */
@Composable
fun <T> adaptiveValue(
    compact: () -> T,
    medium: (() -> T)? = null,
    expanded: (() -> T)? = medium,
): State<T> {
    val windowClass = rememberUpdatedState(
        newValue = currentWindowAdaptiveInfo().windowSizeClass
    )

    return remember {
        derivedStateOf {
            when {
                windowClass.value.atLeastMedium() -> medium?.invoke() ?: compact()
                windowClass.value.atLeastExpanded() -> expanded?.invoke() ?: compact()
                else -> compact()
            }
        }
    }
}
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentCompositeKeyHashCode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lalilu.extensions.ClassicBackHandler


/**
 * Screen 导航栏组件
 *
 * @property key 组件的唯一标识键
 * @property content 组件的可组合内容
 */
data class ScreenBarComponent(
    val key: String,
    val content: @Composable () -> Unit
)


/**
 * 组件栈管理器，用于管理多个 ScreenBarComponent
 */
class ComponentStack {
    var stack: List<ScreenBarComponent> by mutableStateOf(emptyList())

    companion object {
        private val instanceMap = mutableStateMapOf<ScreenBarFactory, ComponentStack>()

        /**
         * 获取指定 ScreenBarFactory 对应的组件栈实例
         */
        fun getInstance(attach: ScreenBarFactory): ComponentStack {
            return instanceMap.getOrPut(attach) { ComponentStack() }
        }
    }
}


/**
 * 接口定义了提供自定义屏幕导航栏的工厂方法
 */
interface ScreenBarFactory {
    /**
     * 获取组件栈实例
     */
    private val stack: ComponentStack
        get() = ComponentStack.getInstance(this)

    /**
     * 获取当前栈顶的导航栏组件
     *
     * @return 返回最后一个组件，如果没有则返回 null
     */
    @Composable
    fun content(): ScreenBarComponent? {
        return stack.stack.lastOrNull()
    }

    /**
     * 注册导航栏内容
     *
     * @param isVisible 是否可见
     * @param onDismiss 关闭时的回调
     * @param onBackPressed 返回按钮按下时的回调，可为 null
     * @param content 导航栏内容
     */
    @Composable
    fun RegisterContent(
        isVisible: () -> Boolean,
        onDismiss: () -> Unit,
        onBackPressed: (() -> Unit)?,
        content: @Composable () -> Unit
    ) {
        val key = currentCompositeKeyHashCode

        LaunchedEffect(isVisible()) {
            if (isVisible()) {
                stack.stack += ScreenBarComponent(
                    key = key.toString(),
                    content = {
                        content.invoke()

                        if (onBackPressed != null) {
                            ClassicBackHandler {
                                onDismiss()
                                onBackPressed()
                            }
                        }
                    }
                )
            } else {
                stack.stack = stack.stack
                    .filter { it.key != key.toString() }
            }
        }
    }
}

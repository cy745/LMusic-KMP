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

import androidx.compose.runtime.*
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

        /**
         * 移除先前创建的组件栈，需要在页面退出时调用
         */
        fun removeInstance(attached: ScreenBarFactory) {
            instanceMap.remove(attached)
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
     * @param onBackPressed 返回按钮按下时的回调，可为 null
     * @param content 导航栏内容
     */
    @Composable
    fun RegisterContent(
        isVisible: () -> Boolean,
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

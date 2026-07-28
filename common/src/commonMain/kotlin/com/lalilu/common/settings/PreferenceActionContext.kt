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

package com.lalilu.common.settings


/**
 * [ClickPreference.onClick] 执行时的运行时上下文。
 *
 * 让 click 回调在不显式依赖 Koin 或业务模块具体类的前提下，
 * 仍能向用户反馈结果（toaster）或触发跳转（navigate）。
 *
 * 默认实现 [Empty] 用 [NoOpToaster] + no-op navigate，方便纯逻辑测试。
 * 真正接入时可在 SettingsScreen 渲染时构造一个连接到 `LocalToaster` /
 * `AppRouter` 的实例，作为 [ClickPreference.onClick] 的 `ctx` 注入。
 */
class PreferenceActionContext(
    val toaster: ToasterLike = NoOpToaster,
    val navigate: (route: String, params: Map<String, Any?>) -> Unit = { _, _ -> }
) {
    companion object {
        /** 静默默认实例：toast 与 navigate 均为 no-op。 */
        val Empty: PreferenceActionContext = PreferenceActionContext()
    }
}


/**
 * 轻量级 Toaster 抽象，避免设置系统反向依赖业务模块的具体 Toaster 实现。
 *
 * 业务侧在 [com.lalilu.lsettings.SettingsScreen] 把真正的 Toaster
 * 适配成此接口传入 [PreferenceActionContext] 即可。
 */
interface ToasterLike {
    fun info(message: String)
    fun warn(message: String)
    fun error(message: String)
}

/** 默认空实现：所有方法都不做任何事。 */
object NoOpToaster : ToasterLike {
    override fun info(message: String) {}
    override fun warn(message: String) {}
    override fun error(message: String) {}
}

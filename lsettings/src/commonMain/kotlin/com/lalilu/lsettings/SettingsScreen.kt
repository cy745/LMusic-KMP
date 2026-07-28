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

package com.lalilu.lsettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import com.lalilu.common.settings.SettingsCollector
import com.lalilu.common.settings.SettingsGroup
import com.lalilu.krouter.annotation.Destination
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenInfo
import com.lalilu.navigation.ScreenInfoFactory
import com.lalilu.remixicon.SystemGroup
import com.lalilu.remixicon.system.settings2Line
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.Factory


/**
 * 收集所有已注册 [SettingsGroup] 的 ViewModel。
 *
 * 当前实现是"加载即拉取"的同步模式；后续若引入"运行时动态加载/卸载模块"
 * 需求，可改为监听 Koin 的 scope 事件并用响应式刷新。
 */
@Factory
class SettingsViewModel : ViewModel() {
    private val _groups = MutableStateFlow<List<SettingsGroup>>(emptyList())
    val groups = _groups.asStateFlow()

    init {
        _groups.value = SettingsCollector.collectAll()
    }
}


/**
 * 设置主页入口。
 *
 * 路由：`/settings`（KRouter）
 *
 * 渲染逻辑：
 * 1. 通过 [SettingsViewModel] 拿到已排序的 [SettingsGroup] 列表
 * 2. 委托给 [SettingsScreenContent] 渲染
 */
@Destination("/settings")
data object SettingsScreen : Screen, ScreenInfoFactory {

    @Composable
    override fun provideScreenInfo(): ScreenInfo = remember {
        ScreenInfo(
            title = { "设置" },
            icon = SystemGroup.settings2Line
        )
    }

    @Composable
    override fun Content() {
        val vm = koinViewModel<SettingsViewModel>()
        val groups by vm.groups.collectAsState()
        SettingsScreenContent(groups = groups)
    }
}

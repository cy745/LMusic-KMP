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

package com.lalilu.lfont

import com.lalilu.common.settings.SettingsGroup
import com.lalilu.common.settings.settingsGroup
import com.lalilu.navigation.AppRouter
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Named

/**
 * 自定义字体的设置分组：提供「字体管理」入口，跳转到字体管理页。
 */
@Factory
@Named("settings_lfont")
fun provideLFontSettings(): SettingsGroup = settingsGroup(
    key = "lfont",
    order = 6,
    title = { "自定义字体" },
    description = { "管理全局界面与歌词字体" },
) {
    click(
        key = "lfont.manage",
        title = { "字体管理" },
        summary = { "导入、管理字体并应用到界面或歌词" },
        onClick = { _ ->
            AppRouter.route("/settings/fonts").jump()
        }
    )
}

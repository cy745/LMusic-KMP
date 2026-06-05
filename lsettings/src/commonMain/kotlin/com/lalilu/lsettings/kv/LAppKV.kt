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

package com.lalilu.lsettings.kv

import com.lalilu.common.kv.KVContext
import com.lalilu.common.kv.KVSaver
import org.koin.core.annotation.Single


/**
 * "app" 前缀的 KV 命名空间，承载 app 级通用设置。
 *
 * 业务模块应当用自己的前缀（"lplayer" / "lhome" / ...），不要复用本类
 * 以避免跨模块 key 冲突。
 *
 * 当前承载的字段：
 * - `dark_mode` —— 全局深色模式开关（默认关闭）
 * - `about_page_url` —— "关于" 页面 URL（默认空）
 */
@Single
class LAppKV(saver: KVSaver) : KVContext(_prefix = "app", _saver = saver) {
    val darkMode = obtain<Boolean>("dark_mode", defaultValue = false)
    val aboutPageUrl = obtain<String>("about_page_url", defaultValue = "")
}

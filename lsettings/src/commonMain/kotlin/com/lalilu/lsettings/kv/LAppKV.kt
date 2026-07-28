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

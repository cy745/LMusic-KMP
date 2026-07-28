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

package com.lalilu.common.testing

import com.lalilu.common.settings.ToasterLike


/**
 * 用于测试 [com.lalilu.common.settings.ClickPreference.onClick] 等场景的假 Toaster。
 *
 * ## 用法
 *
 * - 记录所有发出的消息（按 `info` / `warn` / `error` 等级分桶）
 * - 不做任何 UI 副作用，可在 JVM 单元测试里直接断言调用结果
 *
 * ```
 * val toaster = FakeToaster()
 * val ctx = PreferenceActionContext(toaster = toaster)
 * clickPref.onClick(ctx)
 * assertTrue(toaster.infos.isNotEmpty())
 * ```
 */
class FakeToaster : ToasterLike {
    val infos: MutableList<String> = mutableListOf()
    val warns: MutableList<String> = mutableListOf()
    val errors: MutableList<String> = mutableListOf()

    override fun info(message: String) { infos += message }
    override fun warn(message: String) { warns += message }
    override fun error(message: String) { errors += message }
}

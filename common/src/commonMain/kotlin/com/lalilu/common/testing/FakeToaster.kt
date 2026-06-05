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

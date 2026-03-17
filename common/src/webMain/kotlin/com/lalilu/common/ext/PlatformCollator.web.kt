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

package com.lalilu.common.ext

// 使用 js() 函數定義外部 JS 調用
@OptIn(ExperimentalWasmJsInterop::class)
private fun jsCompare(s1: String, s2: String, locale: String): Int = js("""new Intl.Collator(locale).compare(s1, s2)""")

@Suppress(names = ["ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_MEMBERS_AS_NON_RESTRICTED"])
actual class PlatformCollator actual constructor(private val localeTag: String) {

    actual fun compare(s1: String, s2: String): Int {
        // 調用 JS 的 Intl.Collator API
        return jsCompare(s1, s2, localeTag)
    }
}


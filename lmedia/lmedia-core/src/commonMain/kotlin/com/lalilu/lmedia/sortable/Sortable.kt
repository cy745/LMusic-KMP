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

package com.lalilu.lmedia.sortable

import androidx.annotation.CallSuper

interface Sortable {
    companion object {
        const val COMPARE_KEY_ID: String = "ID"
        const val COMPARE_KEY_TITLE: String = "TITLE"
        const val COMPARE_KEY_SUB_TITLE: String = "SUB_TITLE"
        const val COMPARE_KEY_CREATE_TIME: String = "CREATE_TIME"
        const val COMPARE_KEY_MODIFY_TIME: String = "MODIFY_TIME"
        const val COMPARE_KEY_ITEMS_COUNT: String = "ITEMS_COUNT"
        const val COMPARE_KEY_CONTENT_TYPE: String = "CONTENT_TYPE"
        const val COMPARE_KEY_FILE_SIZE: String = "FILE_SIZE"
        const val COMPARE_KEY_DISK_NUMBER: String = "DISK_NUMBER"
        const val COMPARE_KEY_TRACK_NUMBER: String = "TRACK_NUMBER"
        const val COMPARE_KEY_DURATION: String = "DURATION"
    }

    /**
     * 获取类的指定元素的值的方法
     *
     * 实现时参考以下样例
     * ```kotlin
     *     @Suppress("UNCHECKED_CAST", "IMPLICIT_CAST_TO_ANY")
     *     override fun <T : Any> getValueBy(key: String): T? {
     *         return when (key) {
     *             Sortable.COMPARE_KEY_ID -> id
     *             Sortable.COMPARE_KEY_TITLE -> name
     *             else -> super.getValueBy<T>(key) // 必须传递泛型T给父类方法，否则会导致运行时类型推断错误
     *         } as? T? // 最终强制转换后返回类型为T?
     *     }
     * ```
     */
    @CallSuper
    fun <T : Any> getValueBy(key: String): T? = null
}

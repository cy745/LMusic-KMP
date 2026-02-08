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

package com.lalilu.preview

/**
 * RenderSecurityHelper 是一个用于临时禁用渲染安全检查的辅助对象。
 *
 * 在某些情况下，可能需要临时绕过渲染安全机制以执行特定操作。
 * 此对象提供了一个安全的方式来执行此类操作，确保在操作完成后恢复原有的安全设置。
 */
internal expect object RenderSecurityHelper {

    /**
     * 临时禁用渲染安全检查并执行指定的代码块。
     *
     * @param block 要执行的代码块
     * @return 代码块的返回值
     */
    fun <T> withTemporarilyDisableRenderSecurity(block: () -> T): T
}
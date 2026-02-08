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

@Suppress("PrivateApi")
internal actual object RenderSecurityHelper {

    private val clazz by lazy {
        Class.forName("com.android.tools.rendering.security.RenderSecurityManager")
    }
    private val getCurrentMethod by lazy {
        clazz.getDeclaredMethod("getCurrent").also { it.isAccessible = true }
    }
    private val credentialsMethod by lazy {
        clazz.getDeclaredField("sCredential").also { it.isAccessible = true }
    }
    private val setActiveMethod by lazy {
        clazz.declaredMethods
            .firstOrNull { it.name == "setActive" }
            ?.also { it.isAccessible = true }
    }

    actual fun <T> withTemporarilyDisableRenderSecurity(block: () -> T): T {
        val renderSecurity = runCatching { getCurrentMethod.invoke(null) }
            .getOrNull()

        // jdk 21 以上被禁用了，所以没有 RenderSecurityManager 的时候直接执行并返回
        if (renderSecurity == null) return block()

        val credentials = credentialsMethod.get(renderSecurity)
        return try {
            setActiveMethod?.invoke(renderSecurity, false, credentials)
            block()
        } finally {
            setActiveMethod?.invoke(renderSecurity, true, credentials)
        }
    }
}
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
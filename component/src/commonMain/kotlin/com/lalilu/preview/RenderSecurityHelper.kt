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
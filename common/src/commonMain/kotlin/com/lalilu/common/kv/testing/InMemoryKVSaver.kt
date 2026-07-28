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

package com.lalilu.common.kv.testing

import com.lalilu.common.kv.KVSaver
import kotlin.reflect.KClass


/**
 * 纯内存版 [KVSaver]，供 `commonTest`、CI、调试场景使用。
 *
 * ## 特性
 *
 * - 不依赖 `russhwolf:multiplatform-settings` 等平台实现 → 跨 KMP target 通用
 * - `saveData(null)` 视为删除
 * - 跨测试可通过共享 [store] 模拟"持久化"（同一 `MutableMap` 引用）
 * - 默认值若为 `null` 且 store 中无值，会抛 `IllegalStateException`
 *
 * ## 之所以放在 `commonMain`
 *
 * 让多个模块（lplayer、lhome 等）的测试都能复用同一份测试 KV 实现，
 * 避免每个模块重复造轮子。实现只依赖 `KVSaver` 接口与 [KClass]，
 * 无任何平台 / Compose 依赖，放在 `commonMain` 完全安全。
 */
class InMemoryKVSaver(
    private val store: MutableMap<String, Any?> = mutableMapOf()
) : KVSaver {

    @Suppress("UNCHECKED_CAST")
    override fun <T> readData(key: String, defaultValue: T?, clazz: KClass<*>): T {
        return store[key] as? T ?: defaultValue
            ?: throw IllegalStateException("No value for key=$key and no default provided")
    }

    override fun <T> saveData(key: String, value: T?, clazz: KClass<*>) {
        if (value == null) store.remove(key) else store[key] = value
    }

    /** 导出当前 store 快照，便于断言"持久化结果"。 */
    fun snapshot(): Map<String, Any?> = store.toMap()
}

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

package com.lalilu.common.kv.testing

import com.lalilu.common.kv.KVContext
import com.lalilu.common.kv.KVSaver


/**
 * 简化的 [KVContext] 基类：跳过 Koin 注册 Saver 的步骤，
 * 让测试代码直接持有 [InMemoryKVSaver] 实例。
 *
 * 业务模块的 `LxxxKV`（如 [com.lalilu.lplayer.LPlayerKV]）在测试中也可继承此类，
 * 避免对 Koin 全局状态的依赖。
 *
 * ## 注意
 *
 * - [com.lalilu.common.kv.KVContext] 内部用 `kvMap` 做 key→KVItem 缓存；
 *   跨测试复用同一前缀时**会**命中缓存。如需隔离，每个测试用独立 prefix。
 * - 若在测试中使用了依赖 Koin 的 [com.lalilu.common.kv.KVContext]（如
 *   `obtainList<T>` 需要 `KVConverter`），需要先 `startKoin` 提供 `Json`。
 */
abstract class TestKVContext(
    prefix: String,
    saver: KVSaver
) : KVContext(_prefix = prefix, _saver = saver)

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

package com.lalilu.extensions

import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.Options

/**
 * 通过 Coil 的 [SingletonImageLoader] 获取指定 [data] 的缓存键。
 *
 * 该扩展函数将图片数据经过 Coil 的组件映射（mapping）管线处理后，
 * 生成对应的缓存键（cache key），可用于标识同一输入数据的缓存结果
 * （例如磁盘缓存条目），从而在页面跳转等场景中复用缓存内容。
 *
 * @param data 待解析的图片输入数据（例如 URL、URI 或文件路径）。
 * @param options 可选的 [Options] 参数，用于配置图片加载上下文。
 *                默认为基于当前 [PlatformContext] 构造的 [Options]。
 * @return 若数据可被成功映射并生成缓存键，则返回对应的键字符串；
 *         否则返回 `null`。
 */
fun PlatformContext.retrieveCacheKey(
    data: Any,
    options: Options = Options(this)
): String? {
    val imageLoader = SingletonImageLoader.get(this)
    val mapped = imageLoader.components.map(data, options)
    return imageLoader.components.key(mapped, options)
}
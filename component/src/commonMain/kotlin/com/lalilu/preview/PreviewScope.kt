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

import androidx.compose.runtime.Stable


/**
 * PreviewScope 是一个用于预览功能的上下文管理类，主要用于在 Compose 预览中提供数据支持和配置选项。
 *
 * 该类的核心作用是：
 * 1. 管理预览数据源（dataContext）
 * 2. 提供网络图片加载的相关配置
 * 3. 支持基于泛型的数据筛选和操作
 *
 * 使用场景：
 * - 在 Compose 的 @Preview 注解中创建 PreviewScope 实例
 * - 通过 repeat 或 random 方法筛选特定类型的数据进行预览
 * - 配置网络图片加载行为（如启用网络图片、设置 fallback URL）
 */
class PreviewScope {
    /**
     * 数据上下文，存储所有可用于预览的数据对象
     * 默认初始化为 SongsPreviewData 的内容
     */
    val dataContext: MutableList<Any> = mutableListOf<Any>()
        .apply { addAll(SongsPreviewData) }

    /**
     * 启用网络图片加载功能
     * @return CoilImageHandler 实例，用于链式调用其他配置方法
     */
    fun enableNetworkImage() = CoilImageHandler
        .enableNetworkImage()

    /**
     * 设置网络图片加载失败时的备用图片 URL
     * @param url 备用图片的完整 URL 地址
     * @return CoilImageHandler 实例，用于链式调用其他配置方法
     */
    fun setFallbackUrl(url: String) = CoilImageHandler
        .setFallbackUrl(url)

    /**
     * 重复执行指定次数的操作，适用于需要展示多个相同类型数据的场景
     *
     * @param T 目标数据类型，必须是 dataContext 中存在的类型
     * @param count 要执行的次数
     * @param key 可选的键值，用于筛选特定的 PreviewPresets 数据
     * @param shuffle 是否随机打乱数据顺序
     * @param block 对每个匹配项执行的操作块
     *
     * 使用示例：
     * ```
     * previewScope.repeat<Song>(count = 3) { 
     *     // 对每个 Song 对象执行的操作
     *     Text(text = title)
     * }
     * ```
     */
    @Stable
    inline fun <reified T> repeat(
        count: Int,
        key: String? = null,
        shuffle: Boolean = false,
        block: T.() -> Unit
    ) {
        dataContext.filterIsInstance<T>()
            .run {
                if (T::class.isInstance(PreviewPresets.EMPTY) && key != null) {
                    filterIsInstance<PreviewPresets>()
                        .filter { it.key == key }
                } else {
                    this
                }
            }
            .let { if (shuffle) it.shuffled() else it }
            .take(count)
            .forEach { (it as? T)?.block() }
    }

    /**
     * 随机选择一个数据项并执行操作，适用于只需要展示单个示例的场景
     *
     * @param T 目标数据类型，必须是 dataContext 中存在的类型
     * @param key 可选的键值，用于筛选特定的 PreviewPresets 数据
     * @param block 对选中的数据项执行的操作块
     *
     * 使用示例：
     * ```
     * previewScope.random<Song> { 
     *     // 对随机选中的 Song 对象执行的操作
     *     Text(text = title)
     * }
     * ```
     */
    @Stable
    inline fun <reified T> random(
        key: String? = null,
        block: T.() -> Unit
    ) {
        dataContext.filterIsInstance<T>()
            .run {
                if (T::class.isInstance(PreviewPresets.EMPTY) && key != null) {
                    filterIsInstance<PreviewPresets>()
                        .filter { it.key == key }
                } else {
                    this
                }
            }
            .randomOrNull()
            ?.let { (it as? T)?.block() }
    }
}
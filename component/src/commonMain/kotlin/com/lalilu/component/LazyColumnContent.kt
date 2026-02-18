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

package com.lalilu.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified

/**
 * `LazyColumnContent` 是一个用于定义 LazyColumn 内容的接口，旨在提供一种灵活且可复用的方式来构建列表项。
 * 通过参数化设计，它支持将数据与 UI 解耦，使得开发者可以根据不同的参数动态生成列表内容。
 *
 * ### 核心意义：
 * - **解耦数据与 UI**：通过 `(T) -> Any?` 的参数传递机制，将数据源与 UI 构建逻辑分离，提升代码的可维护性和扩展性。
 * - **动态内容注册**：允许根据运行时参数动态注册不同的列表项，适应多样化的业务场景。
 * - **响应式设计支持**：结合窗口大小适配（如 `adaptiveValue`）和共享元素动画（如 `sharedElementV2`），实现跨设备的优雅 UI 布局。
 *
 * ### 使用方法：
 * 1. **实现接口**：
 *    - 创建一个 object 或 class 实现 `LazyColumnContent<T>`，其中 `T` 通常是枚举类或数据类，用于定义所需的参数。
 *    - 例如：`object CoverHeader : LazyColumnContent<CoverHeader.Param>`
 *
 * 2. **定义参数类型**：
 *    - 在实现类中定义参数类型 `T`，例如枚举类 `Param`，包含所有需要的数据字段（如 `SCOPE`, `COVER`, `TITLE`, `SUBTITLE`）。
 *
 * 3. **重写 `register` 方法**：
 *    - 在 `register` 方法中，通过 `values(param)` 获取对应参数的实际值。
 *    - 使用 Jetpack Compose API 构建 UI 内容，并返回一个 `LazyListScope.() -> Unit` 扩展函数。
 *
 * 4. **集成到 LazyColumn**：
 *    - 将 `register` 方法返回的扩展函数应用到 `LazyColumn` 的作用域中，以添加实际的列表项。
 *
 * ### 示例解析（参考 CoverHeader 实现）：
 * - **参数定义**：`enum class Param { SCOPE, COVER, TITLE, SUBTITLE }` 明确了所需的数据字段。
 * - **动态数据获取**：通过 `values(Param.COVER)` 获取封面图片路径，`values(Param.TITLE)` 获取标题文本等。
 * - **响应式布局**：利用 `windowSizeClass.atLeastMedium()` 判断屏幕尺寸，动态调整布局结构（如横屏 vs 竖屏）。
 * - **共享元素动画**：使用 `sharedElementV2` 和 `sharedBoundsV2` 实现页面间平滑过渡效果。
 *
 * ### 注意事项：
 * - 参数类型 `T` 必须能够准确映射到实际数据，避免类型转换异常。
 * - 返回的 `LazyListScope.() -> Unit` 必须在 LazyColumn 的作用域内调用，否则无法生效。
 * - 若涉及复杂状态管理，建议结合 ViewModel 或 Compose State 进行优化。
 */
@Suppress("UNCHECKED_CAST")
interface LazyColumnContent<T : Any> {
    /**
     * 注册 LazyColumn 的内容项。
     *
     * @param values 一个函数，接收类型为 `T` 的参数，返回任意类型的值（通常用于传递数据）。
     *               该函数的作用是根据传入的参数获取对应的数据，供 LazyColumn 内容使用。
     * @return 返回一个扩展函数，作用于 `LazyListScope`，用于构建 LazyColumn 的具体内容。
     *         该函数会在 LazyColumn 中被调用，以添加实际的 UI 元素。
     */
    @Composable
    fun register(values: (T) -> Any?): LazyListScope.() -> Unit
}

inline fun <T> LazyListScope.gridItems(
    items: List<T>,
    containerModifier: Modifier = Modifier,
    column: Int = 1,
    contentPadding: PaddingValues = PaddingValues(),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(0.dp),
    noinline key: ((item: T) -> Any)? = null,
    noinline contentType: (item: T) -> Any? = { null },
    crossinline itemContent: @Composable LazyItemScope.(item: T) -> Unit,
) {
    val actualItems = items.chunked(column)
        .filter { it.isNotEmpty() }

    itemsIndexed(
        items = actualItems,
        key = key?.let { { index: Int, list: List<T> -> key(list.first()) } },
        contentType = { index: Int, list: List<T> -> contentType(list.first()) }
    ) { index, list ->
        val actualPadding = remember(index, contentPadding) {
            when (index) {
                0 -> contentPadding.copy(bottom = 0.dp)
                actualItems.size - 1 -> contentPadding.copy(top = 0.dp)
                else -> contentPadding.copy(top = 0.dp, bottom = 0.dp)
            }
        }
        val spacingPadding = remember(index, verticalArrangement) {
            when (index) {
                0 -> 0.dp
                else -> verticalArrangement.spacing
            }
        }

        Row(
            modifier = containerModifier
                .padding(paddingValues = actualPadding)
                .padding(top = spacingPadding),
            horizontalArrangement = horizontalArrangement
        ) {
            list.forEach { item ->
                Box(modifier = Modifier.weight(1f)) {
                    itemContent(item)
                }
            }

            // 填充剩余列
            repeat(column - list.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Stable
fun PaddingValues.copy(
    top: Dp = Dp.Unspecified,
    start: Dp = Dp.Unspecified,
    end: Dp = Dp.Unspecified,
    bottom: Dp = Dp.Unspecified
): PaddingValues = object : PaddingValues {
    override fun calculateTopPadding(): Dp = top.takeIf { !it.isUnspecified }
        ?: this@copy.calculateTopPadding()

    override fun calculateBottomPadding(): Dp = bottom.takeIf { !it.isUnspecified }
        ?: this@copy.calculateBottomPadding()

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = start.takeIf { !it.isUnspecified }
        ?: this@copy.calculateLeftPadding(layoutDirection)

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = end.takeIf { !it.isUnspecified }
        ?: this@copy.calculateRightPadding(layoutDirection)
}
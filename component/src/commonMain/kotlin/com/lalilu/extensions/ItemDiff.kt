package com.lalilu.extensions

import kotlin.random.Random

/**
 * Item/列表差分工具（KMP 通用）。
 *
 * 使用场景：
 * - 在 Compose 的 `LazyColumn`/`LazyRow`/`LazyVerticalGrid` 中需要稳定的 `key` 以保证动画、滚动位置、重组稳定性。
 * - 当数据源发生增删改（尤其是新增）时，为新增项生成不会与旧项冲突的 `key`，未变化项沿用旧 `key`。
 * - 不依赖 Android 的 `DiffUtil`，在 Kotlin Multiplatform 环境下复用的差分方案。
 *
 * 核心行为：
 * - 通过 LCS（最长公共子序列）保留旧列表与新列表中“相等”的元素，并沿用旧 `key`；
 * - 对新出现的元素生成 `"${generation}_${getId(item)}"` 作为新 `key`；
 * - 删除项不出现在结果中；移动视为删除+插入（效果与 `DiffUtil` 类似）。
 *
 * 典型用法：
 * ```kotlin
 * // 业务数据列表 -> 视图数据列表（带稳定 key）
 * var actualItems: List<Item<Media>> = emptyList()
 * val newItems: List<Media> = fetchFromRepository()
 * actualItems = actualItems.diff(newItems) { it.id }
 *
 * LazyColumn {
 *     itemsIndexed(
 *         items = actualItems,
 *         key = { _, item -> item.key },
 *     ) { _, item ->
 *         // 使用 item.data 渲染 UI
 *     }
 * }
 * ```
 *
 * 注意事项：
 * - `T` 的相等性基于 `equals` 比较；建议使用 `data class` 或正确实现 `equals/hashCode`。
 * - `getId` 应返回业务稳定 ID（如数据库/媒体 ID），用于生成新增项的 `key`。
 * - 每次 `diff` 都会生成新的 `generation` 前缀，确保新增项的 `key` 不与旧项冲突。
 */
data class Item<T>(
    val data: T,
    val key: String
)

/**
 * 计算旧 `Item<T>` 列表与新 `items: List<T>` 的差分，返回带稳定 `key` 的新列表。
 *
 * - 未变化项：沿用旧 `key`，保证 Compose 稳定性与动画正确性；
 * - 新增项：使用 `"${generation}_${getId(item)}"` 生成新 `key`；
 * - 删除项：不包含在返回列表；
 * - 移动项：视为删除+插入（与 `DiffUtil` 效果一致）。
 *
 * @param items 新的数据列表（业务数据）
 * @param getId 获取业务稳定 ID 的函数，用于生成新增项的 `key`
 * @return 新的 `Item<T>` 列表（可直接用于 Compose `key`）
 */
fun <T : Any> List<Item<T>>.diff(
    items: List<T>,
    getId: (T) -> String
): List<Item<T>> {
    val oldData = this.map { it.data }
    val n = oldData.size
    val m = items.size

    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) {
        for (j in m - 1 downTo 0) {
            dp[i][j] = if (oldData[i] == items[j]) dp[i + 1][j + 1] + 1
            else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
    }

    val generation = Random.nextLong().toString()
    val result = ArrayList<Item<T>>(m)
    var i = 0
    var j = 0
    while (i < n && j < m) {
        val a = oldData[i]
        val b = items[j]
        if (a == b) {
            result.add(this[i])
            i++
            j++
        } else {
            if (dp[i + 1][j] >= dp[i][j + 1]) {
                i++
            } else {
                result.add(Item(data = b, key = "${generation}_${getId(b)}"))
                j++
            }
        }
    }
    while (j < m) {
        val b = items[j]
        result.add(Item(data = b, key = "${generation}_${getId(b)}"))
        j++
    }
    return result
}
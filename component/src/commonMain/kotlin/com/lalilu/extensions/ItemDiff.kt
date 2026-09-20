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
 * @param isSameItem 旧项与新项是否为同一元素
 * @param isSameContent 旧项与新项内容是否相同
 * @return 新的 `Item<T>` 列表（可直接用于 Compose `key`）
 */
fun <T : Any> List<Item<T>>.diff(
    items: List<T>,
    getId: (T) -> String,
    isSameItem: (T, T) -> Boolean = { a, b -> a == b },
    isSameContent: (T, T) -> Boolean = { a, b -> a == b }
): List<Item<T>> {
    val oldData = this.map { it.data }
    val n = oldData.size
    val m = items.size

    // 使用 LCS 算法计算最长公共子序列，基于 isSameItem 判断是否为同一元素
    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) {
        for (j in m - 1 downTo 0) {
            dp[i][j] = if (isSameItem(oldData[i], items[j])) dp[i + 1][j + 1] + 1
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
        if (isSameItem(a, b)) {
            // 如果是同一个元素，检查内容是否相同
            if (isSameContent(a, b)) {
                // 内容也相同，直接复用旧的 Item（包括 key）
                result.add(this[i])
            } else {
                // 是同一个元素但内容不同，更新数据但保留 key
                result.add(Item(data = b, key = this[i].key))
            }
            i++
            j++
        } else {
            // 不是同一个元素，根据 DP 表决定是跳过旧元素还是添加新元素
            if (dp[i + 1][j] >= dp[i][j + 1]) {
                i++
            } else {
                result.add(Item(data = b, key = "${generation}_${getId(b)}"))
                j++
            }
        }
    }
    // 处理剩余的新元素
    while (j < m) {
        val b = items[j]
        result.add(Item(data = b, key = "${generation}_${getId(b)}"))
        j++
    }
    return result
}

/**
 * 以「新列表首元素」为切点做差分：把旧列表里排在它前面的那一整块视为「离场」，在末尾以新
 * key 重新出现；其余元素沿用旧 key 并整体上移。
 *
 * 与 [diff] 的区别：[diff] 基于 LCS，总是保留更长的公共子序列。当新列表是旧列表左旋 p 位时，
 * 它会在「保留旧前块 p 个」与「保留旧尾块 n-p 个」里挑更长的那个，于是 p > n/2 时会翻转成
 * 「旧前块保留、旧尾块被挪到队首」；表现就是大幅跳跃时该消失的明明是队首那一段，结果却反了
 * 过来（保留的元素整体被推到末尾，滚动锚点随之跑到列表末尾）。
 *
 * 本方法固定采用「切点之前的整块搬到末尾」的语义，与旋转幅度无关：
 * - p 个元素从队首淡出（旧 key 消失），其余保持 key 上移；
 * - 同一批元素在末尾以新 key 出现（淡入）。
 *
 * 唯一例外：新队首原本就是旧列表的最后一个元素（`p == n-1`）。这个位置天然有歧义——它既可能
 * 是「上一首」（队尾回到队首），也可能只是「点击了列表里的最后一行」。两种成因的列表完全一样，
 * 只看新旧列表无法区分，所以：
 * - 默认按「上一首」处理：只让新队首自己换新 key、其余整体下移（表现为「顶部淡入一行」）；若照搬
 *   常规规则，会退化成「几乎整表淡出再淡入」；
 * - 调用方知道成因时（例如记录到用户点了列表里的某一行），把 [isRowTap] 传 `true`，此时 `p == n-1`
 *   也走常规规则：被点中的那一行保留旧 key（滚动锚点不丢），它前面的整块换新 key 并在末尾出现。
 *
 * @param items 新的数据列表
 * @param getId 生成新 key 用的稳定 id
 * @param isSameItem 是否为同一个元素，用于判断旋转关系
 * @param isSameContent 内容是否相同；相同则直接复用旧 [Item]（含旧 key），不同则保留旧 key 换新内容
 * @param isRowTap 新队首是否为「用户点击列表中的该行」的结果。仅影响 `p == n-1` 这一种歧义情形
 */
fun <T : Any> List<Item<T>>.rotationalDiff(
    items: List<T>,
    getId: (T) -> String,
    isSameItem: (T, T) -> Boolean = { a, b -> a == b },
    isSameContent: (T, T) -> Boolean = { a, b -> a == b },
    isRowTap: Boolean = false
): List<Item<T>> {
    /** 不构成旋转时统一走原有实现，保证增删 / 同步等场景行为不变 */
    fun fallback(): List<Item<T>> = diff(items, getId, isSameItem, isSameContent)

    val n = size
    if (n == 0 || items.size != n) return fallback()

    // 新列表首元素必须在旧列表中唯一存在，否则无法确定切点
    val head = items.first()
    val headIndex = indices.filter { isSameItem(this[it].data, head) }
    if (headIndex.size != 1) return fallback()
    val pivot = headIndex.first()

    // 校验新列表恰好是旧列表左旋 pivot 位
    for (i in 0 until n) {
        if (!isSameItem(this[(pivot + i) % n].data, items[i])) return fallback()
    }

    // 「上一首」：新队首原本在队尾，只让它自己换新 key。
    // 已被证实是「点击最后一行」时不算「上一首」，照常走常规规则。
    val singleHeadStep = pivot == n - 1 && n > 1 && !isRowTap

    val generation = Random.nextLong().toString()
    return List(n) { i ->
        val data = items[i]
        // -1 表示这是需要新 key 的元素
        val oldIndex = when {
            // 「上一首」：新队首换新 key，其余整体下移一位、沿用旧 key
            singleHeadStep -> i - 1
            // 常规：切点之前的整块上移并沿用旧 key，其后的（= 旧列表的队首段）换新 key
            i < n - pivot -> pivot + i
            else -> -1
        }
        if (oldIndex < 0) {
            Item(data = data, key = "${generation}_${getId(data)}")
        } else {
            val old = this[oldIndex]
            if (isSameContent(old.data, data)) old else Item(data = data, key = old.key)
        }
    }
}

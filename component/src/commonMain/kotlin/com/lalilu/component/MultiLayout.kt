package com.lalilu.component

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lalilu.preview.preview
import kotlin.random.Random

@DslMarker
annotation class MultiLayoutDsl

private const val UNIVERSE_COLUMN = 12
internal const val ROOT_GAP_SCOPE_ID = 0

data class MultiLayoutContext(
    val contentPadding: PaddingValues = PaddingValues(),
    val span: Int = UNIVERSE_COLUMN,
    val enableAnimateItem: Boolean = false,
    val horizontalGap: Dp = 0.dp,
    val verticalGap: Dp = 0.dp
)

/**
 * 一次 [gap] 调用对应一个作用域节点。
 *
 * 节点保留父级与深度，用于寻找两个 item 最近的公共 gap 作用域：同一 gap 内部使用当前值，
 * 跨越嵌套 gap 边界时回退到共同的父级值，互不相关的顶层 gap 之间最终回退到根作用域的 0.dp。
 */
internal data class MultiLayoutGapScope(
    val id: Int,
    val parentId: Int?,
    val depth: Int,
    val horizontalGap: Dp,
    val verticalGap: Dp
)

/**
 * 单个 LazyGrid item 在本轮 DSL 构建中的静态布局信息。
 *
 * [startColumn]、[endColumn] 使用 12 列逻辑坐标；[resolvedPadding] 会在所有 item 注册完毕后
 * 统一计算。Lazy item 捕获本对象后，即使后续发生重组，也不会读到另一轮构建的数据。
 */
internal class MultiLayoutItemPlan(
    val index: Int,
    val span: Int,
    val line: Int,
    val startColumn: Int,
    val endColumn: Int,
    val contentPadding: PaddingValues,
    val gapScopeId: Int
) {
    var resolvedPadding: PaddingValues = PaddingValues()
        internal set
}

/**
 * 收集一次 [LazyGridScope] DSL 执行产生的完整布局计划。
 *
 * DSL 注册阶段只记录 item 的顺序、span、物理行与 gap 作用域；[finish] 阶段在掌握整行信息后
 * 再统一求解 padding。每轮构建使用独立 session，避免旧的 Lazy item lambda 在重组后读取到
 * 已被重置或属于新一轮构建的全局状态。
 */
internal class MultiLayoutBuildSession(
    private val layoutDirection: LayoutDirection
) {
    private val scopes = mutableMapOf(
        ROOT_GAP_SCOPE_ID to MultiLayoutGapScope(
            id = ROOT_GAP_SCOPE_ID,
            parentId = null,
            depth = 0,
            horizontalGap = 0.dp,
            verticalGap = 0.dp
        )
    )
    private val items = mutableListOf<MultiLayoutItemPlan>()

    private var nextScopeId = ROOT_GAP_SCOPE_ID + 1
    private var currentLine = 0
    private var currentColumn = 0
    private var finished = false

    val itemCount: Int
        get() = items.size

    val nextColumn: Int
        get() = currentColumn

    fun createGapScope(
        parentId: Int,
        horizontalGap: Dp,
        verticalGap: Dp
    ): Int {
        check(!finished) { "Cannot add a gap scope to a finished MultiLayout plan." }
        require(horizontalGap >= 0.dp) { "horizontalGap must be non-negative." }
        require(verticalGap >= 0.dp) { "verticalGap must be non-negative." }

        val parent = scopes.getValue(parentId)
        val id = nextScopeId++
        scopes[id] = MultiLayoutGapScope(
            id = id,
            parentId = parentId,
            depth = parent.depth + 1,
            horizontalGap = horizontalGap,
            verticalGap = verticalGap
        )
        return id
    }

    fun addItems(
        count: Int,
        span: Int,
        contentPadding: PaddingValues,
        gapScopeId: Int
    ): List<MultiLayoutItemPlan> {
        check(!finished) { "Cannot add items to a finished MultiLayout plan." }
        require(count >= 0) { "count must be non-negative." }
        require(span in 1..UNIVERSE_COLUMN) {
            "span must be between 1 and $UNIVERSE_COLUMN, but was $span."
        }
        scopes.getValue(gapScopeId)

        return List(count) {
            // 当前行剩余列不足时，Compose Grid 会先换行再放置该 item。
            if (currentColumn + span > UNIVERSE_COLUMN) {
                currentLine += 1
                currentColumn = 0
            }

            val item = MultiLayoutItemPlan(
                index = items.size,
                span = span,
                line = currentLine,
                startColumn = currentColumn,
                endColumn = currentColumn + span,
                contentPadding = contentPadding,
                gapScopeId = gapScopeId
            )
            items += item

            currentColumn = item.endColumn
            if (currentColumn == UNIVERSE_COLUMN) {
                currentLine += 1
                currentColumn = 0
            }
            item
        }
    }

    fun finish() {
        check(!finished) { "MultiLayout plan has already been finished." }
        finished = true
        if (items.isEmpty()) return

        // 注册时已经确定物理行号，这里按行聚合，后续可以同时观察相邻 item 与相邻物理行。
        val lines = mutableListOf<MutableList<MultiLayoutItemPlan>>()
        items.forEach { item ->
            while (lines.size <= item.line) lines += mutableListOf<MultiLayoutItemPlan>()
            lines[item.line] += item
        }

        lines.forEachIndexed { lineIndex, lineItems ->
            val verticalGap = if (lineIndex == 0) 0.dp else {
                resolveVerticalGap(
                    previousLine = lines[lineIndex - 1],
                    currentLine = lineItems
                )
            }
            resolveHorizontalPaddings(lineItems, verticalGap)
        }
    }

    /**
     * 求解一条物理行内所有 item 的水平 padding。
     *
     * 均匀残缺行优先使用虚拟轨道算法，以保持与完整 Grid 行一致的 item 宽度；其余情况使用
     * 实际边界算法，使同行不同 gap、不同 span 和嵌套作用域仍能得到明确结果。
     */
    private fun resolveHorizontalPaddings(
        lineItems: List<MultiLayoutItemPlan>,
        verticalGap: Dp
    ) {
        if (lineItems.isEmpty()) return
        if (resolveUniformIncompleteLine(lineItems, verticalGap)) return

        val lineStart = lineItems.first()
            .takeIf { it.startColumn == 0 }
            ?.contentPadding
            ?.calculateStartPadding(layoutDirection)
            ?: 0.dp
        val lineEnd = lineItems.last()
            .takeIf { it.endColumn == UNIVERSE_COLUMN }
            ?.contentPadding
            ?.calculateEndPadding(layoutDirection)
            ?: 0.dp
        val boundaries = lineItems.zipWithNext { left, right ->
            nearestCommonScope(left.gapScopeId, right.gapScopeId).horizontalGap
        }
        val totalPadding = boundaries.fold(lineStart + lineEnd) { total, gap -> total + gap }
        val totalSpan = lineItems.sumOf { it.span }.toFloat()

        // 实际边界算法的优先级：边界 gap 和行首尾 padding 准确、padding 非负、最后才是宽度均衡。
        // 条件允许时按 span 比例分配总 padding，使每个逻辑列的可见宽度尽量一致；若比例分配
        // 需要负 padding，则把边界分配限制在 0..gap，接受局部宽度差异。
        var itemStart = lineStart
        lineItems.forEachIndexed { index, item ->
            val itemEnd = if (index == lineItems.lastIndex) {
                lineEnd
            } else {
                val boundary = boundaries[index]
                val targetPadding = totalPadding * (item.span.toFloat() / totalSpan)
                (targetPadding - itemStart).coerceIn(0.dp, boundary)
            }

            item.resolvedPadding = PaddingValues(
                start = itemStart,
                top = verticalGap,
                end = itemEnd
            )

            if (index < lineItems.lastIndex) {
                itemStart = boundaries[index] - itemEnd
            }
        }
    }

    /**
     * 为均匀残缺行补齐“虚拟轨道”，使实际 item 与完整行中相同位置的 item 保持同宽。
     *
     * 例如一行理论上可容纳 3 个 span=4 的 item，但最后一行只有第一个 item，此处仍按
     * `[实际 item, 虚拟 item, 虚拟 item]` 计算三轨道 padding，只把对应结果写回实际 item。
     * 虚拟轨道不会注册到 LazyGrid，也不会参与组合、测量或索引。
     *
     * 只有满足以下条件时才能唯一推导虚拟布局：
     * - 当前行尚未占满 12 列；
     * - span 能整除 12；
     * - 所有实际 item 的 span、gap 作用域和水平 contentPadding 相同；
     * - item 从行首开始连续占据对应轨道。
     *
     * 混合 span、同行不同 gap 或不同 contentPadding 没有唯一的虚拟结构，返回 false，交由
     * 实际边界算法处理。
     */
    private fun resolveUniformIncompleteLine(
        lineItems: List<MultiLayoutItemPlan>,
        verticalGap: Dp
    ): Boolean {
        val first = lineItems.first()
        if (lineItems.last().endColumn == UNIVERSE_COLUMN) return false
        if (UNIVERSE_COLUMN % first.span != 0) return false

        val lineStart = first.contentPadding.calculateStartPadding(layoutDirection)
        val lineEnd = first.contentPadding.calculateEndPadding(layoutDirection)
        val isUniform = lineItems.allIndexed { index, item ->
            item.span == first.span &&
                    item.gapScopeId == first.gapScopeId &&
                    item.startColumn == index * first.span &&
                    item.contentPadding.calculateStartPadding(layoutDirection) == lineStart &&
                    item.contentPadding.calculateEndPadding(layoutDirection) == lineEnd
        }
        if (!isUniform) return false

        val trackCount = UNIVERSE_COLUMN / first.span
        val gap = scopes.getValue(first.gapScopeId).horizontalGap
        val totalPadding = lineStart + lineEnd + gap * (trackCount - 1)
        val targetTrackPadding = totalPadding / trackCount.toFloat()

        var trackStart = lineStart
        repeat(trackCount) { trackIndex ->
            val trackEnd = if (trackIndex == trackCount - 1) {
                lineEnd
            } else {
                (targetTrackPadding - trackStart).coerceIn(0.dp, gap)
            }

            lineItems.getOrNull(trackIndex)?.resolvedPadding = PaddingValues(
                start = trackStart,
                top = verticalGap,
                end = trackEnd
            )
            trackStart = gap - trackEnd
        }
        return true
    }

    /**
     * 垂直 gap 属于物理行边界。遍历上下两行的 item 组合，取它们最近公共 gap 作用域中的
     * 最大 verticalGap，并统一应用到下一行，避免同一行 item 顶部不对齐。
     */
    private fun resolveVerticalGap(
        previousLine: List<MultiLayoutItemPlan>,
        currentLine: List<MultiLayoutItemPlan>
    ): Dp {
        var result = 0.dp
        previousLine.forEach { previous ->
            currentLine.forEach { current ->
                val gap = nearestCommonScope(
                    leftId = previous.gapScopeId,
                    rightId = current.gapScopeId
                ).verticalGap
                if (gap > result) result = gap
            }
        }
        return result
    }

    /** 查找两个 item 最近的公共 gap 作用域，决定它们共享边界上的 gap。 */
    private fun nearestCommonScope(leftId: Int, rightId: Int): MultiLayoutGapScope {
        var left = scopes.getValue(leftId)
        var right = scopes.getValue(rightId)

        while (left.depth > right.depth) left = scopes.getValue(left.parentId!!)
        while (right.depth > left.depth) right = scopes.getValue(right.parentId!!)
        while (left.id != right.id) {
            left = scopes.getValue(left.parentId!!)
            right = scopes.getValue(right.parentId!!)
        }
        return left
    }

    private inline fun <T> List<T>.allIndexed(predicate: (index: Int, T) -> Boolean): Boolean {
        forEachIndexed { index, item ->
            if (!predicate(index, item)) return false
        }
        return true
    }
}

class MultiLayoutGlobalData {
    var compositionIndex: Int = 0
    var compositionCurrentLine: Int = 0

    private var currentSession: MultiLayoutBuildSession? = null

    // gap DSL 通过同步嵌套调用形成作用域栈；退出 gap 时必须恢复父级，不能把局部设置泄漏出去。
    private var currentGapScopeId: Int = ROOT_GAP_SCOPE_ID

    internal fun beginBuild(layoutDirection: LayoutDirection): MultiLayoutBuildSession {
        check(currentSession == null) { "A MultiLayout plan is already being built." }
        resetIndex()
        resetSpan()
        currentGapScopeId = ROOT_GAP_SCOPE_ID
        return MultiLayoutBuildSession(layoutDirection).also { currentSession = it }
    }

    internal fun finishBuild(session: MultiLayoutBuildSession) {
        check(currentSession === session) { "Trying to finish an inactive MultiLayout plan." }
        try {
            session.finish()
        } finally {
            currentSession = null
            currentGapScopeId = ROOT_GAP_SCOPE_ID
        }
    }

    internal fun abortBuild(session: MultiLayoutBuildSession) {
        if (currentSession === session) {
            currentSession = null
            currentGapScopeId = ROOT_GAP_SCOPE_ID
        }
    }

    internal fun createGapScope(
        horizontalGap: Dp,
        verticalGap: Dp
    ): Int = requireNotNull(currentSession) {
        "gap() can only be used while building MultiLayout content."
    }.createGapScope(currentGapScopeId, horizontalGap, verticalGap)

    internal fun <T> withGapScope(scopeId: Int, block: () -> T): T {
        val parentScopeId = currentGapScopeId
        currentGapScopeId = scopeId
        return try {
            block()
        } finally {
            currentGapScopeId = parentScopeId
        }
    }

    internal fun addItems(
        count: Int,
        span: Int,
        contentPadding: PaddingValues,
        gapScopeId: Int
    ): List<MultiLayoutItemPlan> {
        val session = requireNotNull(currentSession) {
            "Items can only be added while building MultiLayout content."
        }
        val result = session.addItems(count, span, contentPadding, gapScopeId)
        compositionIndex = session.itemCount
        compositionCurrentLine = session.nextColumn
        return result
    }

    internal fun addItems(
        count: Int,
        span: Int,
        contentPadding: PaddingValues
    ): List<MultiLayoutItemPlan> = addItems(
        count = count,
        span = span,
        contentPadding = contentPadding,
        gapScopeId = currentGapScopeId
    )

    fun increaseIndex(count: Int = 1) {
        compositionIndex += count
    }

    fun resetIndex() {
        compositionIndex = 0
    }

    fun increaseSpan(span: Int, count: Int = 1) {
        repeat(count) {
            val targetValue = compositionCurrentLine + span
            compositionCurrentLine = when {
                targetValue == UNIVERSE_COLUMN -> 0
                targetValue > UNIVERSE_COLUMN -> span
                else -> targetValue
            }
        }
    }

    fun precalcEndSpan(startValue: Int, span: Int, index: Int = 0): Int {
        var tempValue = startValue
        repeat(index + 1) {
            val targetValue = tempValue + span
            tempValue = when {
                // 大于列数，说明该行放不下，需要换行再放入
                targetValue > UNIVERSE_COLUMN -> span
                else -> targetValue
            }
        }
        return tempValue
    }

    fun resetSpan() {
        compositionCurrentLine = 0
    }
}

fun MultiLayoutScope.copyContext(
    mapper: MultiLayoutContext.() -> MultiLayoutContext
): MultiLayoutScope {
    val overrideContext = context.mapper()
    return object : MultiLayoutScope by this {
        override val context: MultiLayoutContext = overrideContext
    }
}

@MultiLayoutDsl
interface MultiLayoutGlobalScope {
    val global: MultiLayoutGlobalData
}

@MultiLayoutDsl
interface MultiLayoutContextScope : MultiLayoutGlobalScope {
    val context: MultiLayoutContext

    fun MultiLayoutScope.contentPadding(
        contentPadding: PaddingValues = PaddingValues(),
        content: MultiLayoutScope.() -> Unit = {}
    ) {
        this@contentPadding
            .copyContext { copy(contentPadding = contentPadding) }
            .content()
    }

    fun MultiLayoutScope.span(
        span: Int = UNIVERSE_COLUMN,
        content: MultiLayoutScope.() -> Unit = {}
    ) {
        this@span
            .copyContext { copy(span = span.coerceAtMost(UNIVERSE_COLUMN)) }
            .content()
    }

    /**
     * 设置当前作用域后代之间的间距，不额外生成作用域外边距。
     *
     * 水平方向由相邻 item 最近的公共 gap 作用域决定，因此嵌套 gap 只影响其内部，两个独立
     * gap 块之间会回退到共同父级的 gap。垂直方向以物理行边界为单位，使用跨越该边界的
     * 有效公共作用域中最大的 verticalGap，保证整行对齐。
     */
    fun MultiLayoutScope.gap(
        horizontalGap: Dp = context.horizontalGap,
        verticalGap: Dp = context.verticalGap,
        content: MultiLayoutScope.() -> Unit = {}
    ) {
        val gapScopeId = global.createGapScope(
            horizontalGap = horizontalGap,
            verticalGap = verticalGap
        )
        global.withGapScope(gapScopeId) {
            this@gap.copyContext {
                copy(
                    horizontalGap = horizontalGap,
                    verticalGap = verticalGap
                )
            }.content()
        }
    }
}


interface MultiLayoutLazyScope : MultiLayoutContextScope {

    context(gridScope: LazyGridScope)
    fun MultiLayoutScope.divider(
        key: Any? = null,
        contentType: Any? = null,
        span: Int = UNIVERSE_COLUMN,
        paddingValues: PaddingValues = context.contentPadding,
        content: @Composable LazyGridItemScope.(Int) -> Unit = { HorizontalDivider() }
    ) {
        val layoutItem = global.addItems(
            count = 1,
            span = span,
            contentPadding = paddingValues
        ).single()

        gridScope.item(
            key = key,
            contentType = contentType,
            span = { GridItemSpan(span) }
        ) {
            Box(
                modifier = Modifier.padding(layoutItem.resolvedPadding)
            ) {
                content(layoutItem.index)
            }
        }
    }

    context(gridScope: LazyGridScope)
    fun MultiLayoutScope.item(
        key: Any? = null,
        contentType: Any? = null,
        span: Int = context.span,
        paddingValues: PaddingValues = context.contentPadding,
        content: @Composable LazyGridItemScope.(Int) -> Unit
    ) {
        val layoutItem = global.addItems(
            count = 1,
            span = span,
            contentPadding = paddingValues
        ).single()

        gridScope.item(
            key = key,
            contentType = contentType,
            span = { GridItemSpan(span) }
        ) {
            Box(
                modifier = Modifier.padding(layoutItem.resolvedPadding)
            ) {
                content(layoutItem.index)
            }
        }
    }

    context(gridScope: LazyGridScope)
    fun MultiLayoutScope.items(
        count: Int,
        key: ((index: Int) -> Any)? = null,
        contentType: (index: Int) -> Any? = { null },
        span: Int = context.span,
        paddingValues: PaddingValues = context.contentPadding,
        content: @Composable LazyGridItemScope.(index: Int) -> Unit
    ) {
        val layoutItems = global.addItems(
            count = count,
            span = span,
            contentPadding = paddingValues
        )

        gridScope.items(
            count = count,
            key = key,
            contentType = contentType,
            span = { GridItemSpan(span) }
        ) { offsetIndex ->
            val layoutItem = layoutItems[offsetIndex]

            Box(
                modifier = Modifier.padding(layoutItem.resolvedPadding)
            ) {
                content(layoutItem.index)
            }
        }
    }

    context(gridScope: LazyGridScope)
    fun <T> MultiLayoutScope.items(
        items: List<T>,
        key: ((index: Int, item: T) -> Any)? = null,
        contentType: (index: Int, item: T) -> Any? = { _, _ -> null },
        span: Int = context.span,
        paddingValues: PaddingValues = context.contentPadding,
        content: @Composable LazyGridItemScope.(Int, T) -> Unit
    ) {
        val layoutItems = global.addItems(
            count = items.size,
            span = span,
            contentPadding = paddingValues
        )

        gridScope.itemsIndexed(
            items = items,
            key = key,
            contentType = contentType,
            span = { index, item -> GridItemSpan(span) }
        ) { offsetIndex, item ->
            val layoutItem = layoutItems[offsetIndex]

            Box(
                modifier = Modifier.padding(layoutItem.resolvedPadding)
            ) {
                content(layoutItem.index, item)
            }
        }
    }
}

@MultiLayoutDsl
interface MultiLayoutScope : MultiLayoutLazyScope {
    fun doComposite(
        layoutDirection: LayoutDirection,
        layoutContent: context(LazyGridScope) MultiLayoutScope.() -> Unit
    ): LazyGridScope.() -> Unit {
        return {
            val session = global.beginBuild(layoutDirection)
            try {
                layoutContent()
                global.finishBuild(session)
            } catch (throwable: Throwable) {
                global.abortBuild(session)
                throw throwable
            }
        }
    }
}

@Composable
fun rememberMultiLayoutScope(): MultiLayoutScope {
    return remember {
        val context = MultiLayoutContext()
        val global = MultiLayoutGlobalData()
        object : MultiLayoutScope {

            override val context: MultiLayoutContext = context
            override val global: MultiLayoutGlobalData = global
        }
    }
}

@Composable
fun MultiLayout(
    modifier: Modifier = Modifier,
    scope: MultiLayoutScope = rememberMultiLayoutScope(),
    state: LazyGridState = rememberLazyGridState(),
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    userScrollEnabled: Boolean = true,
    reverseLayout: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(),
    verticalArrangement: Arrangement.Vertical =
        if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    layoutContent: context(LazyGridScope) MultiLayoutScope.() -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current

    LazyVerticalGrid(
        modifier = modifier,
        state = state,
        userScrollEnabled = userScrollEnabled,
        overscrollEffect = overscrollEffect,
        reverseLayout = reverseLayout,
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding()
        ),
        verticalArrangement = verticalArrangement,
        horizontalArrangement = horizontalArrangement,
        columns = GridCells.Fixed(UNIVERSE_COLUMN),
        content = scope.doComposite(layoutDirection, layoutContent)
    )
}

@Preview
@Composable
fun MultiLayoutPreview() = preview {
    MultiLayout(
        modifier = Modifier.fillMaxHeight(1f),
        reverseLayout = false,
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        contentPadding(contentPadding = PaddingValues(horizontal = 16.dp)) {
            gap(horizontalGap = 16.dp, verticalGap = 16.dp) {
                span(12) {
                    item { index ->
                        TestItem(
                            modifier = Modifier,
                            index = index
                        )
                    }
                }

                divider(span = 12)

                span(4) {
                    item { index ->
                        TestItem(
                            modifier = Modifier,
                            index = index
                        )
                    }
                }

                items(count = 3, span = 4) { index ->
                    TestItem(
                        modifier = Modifier,
                        index = index
                    )
                }

                gap(verticalGap = 8.dp) {
                    items(items = listOf("test1", "test2")) { index, item ->
                        TestItem(
                            modifier = Modifier,
                            index = index,
                            content = item
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TestItem(
    modifier: Modifier = Modifier,
    index: Int = 0,
    content: String = Random.nextBytes(16).toHexString()
) {
    Text(
        modifier = modifier.fillMaxWidth()
            .border(width = 1.dp, color = MaterialTheme.colorScheme.onBackground.copy(0.1f))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        text = "[$index] TEST: $content"
    )
}

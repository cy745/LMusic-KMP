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
import androidx.compose.ui.unit.dp
import com.lalilu.preview.preview
import kotlin.random.Random

@DslMarker
annotation class MultiLayoutDsl

private const val UNIVERSE_COLUMN = 12

data class MultiLayoutContext(
    val contentPadding: PaddingValues = PaddingValues(),
    val span: Int = UNIVERSE_COLUMN,
    val enableAnimateItem: Boolean = false,
    val horizontalGap: Dp = 0.dp,
    val verticalGap: Dp = 0.dp
)

class MultiLayoutGlobalData {
    var compositionIndex: Int = 0
    var compositionCurrentLine: Int = 0

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

    fun MultiLayoutScope.gap(
        horizontalGap: Dp = context.horizontalGap,
        verticalGap: Dp = context.verticalGap,
        content: MultiLayoutScope.() -> Unit = {}
    ) {
        this@gap.copyContext {
            copy(horizontalGap = horizontalGap, verticalGap = verticalGap)
        }.content()
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
        val startIndex = global.compositionIndex
        val startCurrentLineSpan = global.compositionCurrentLine

        gridScope.item(
            key = key,
            contentType = contentType,
            span = { GridItemSpan(span) }
        ) {
            val reachLineStart = startCurrentLineSpan == 0
            val reachLineEnd = startCurrentLineSpan + span == UNIVERSE_COLUMN

            Box(
                modifier = Modifier.padding(
                    paddingValues = paddingValues.applyWithLinePosition(reachLineStart, reachLineEnd)
                )
            ) {
                content(startIndex)
            }
        }
        global.increaseSpan(span = span)
        global.increaseIndex()
    }

    context(gridScope: LazyGridScope)
    fun MultiLayoutScope.item(
        key: Any? = null,
        contentType: Any? = null,
        span: Int = context.span,
        paddingValues: PaddingValues = context.contentPadding,
        content: @Composable LazyGridItemScope.(Int) -> Unit
    ) {
        val startIndex = global.compositionIndex
        val startCurrentLineSpan = global.compositionCurrentLine

        gridScope.item(
            key = key,
            contentType = contentType,
            span = { GridItemSpan(span) }
        ) {
            val reachLineStart = startCurrentLineSpan == 0
            val reachLineEnd = startCurrentLineSpan + span == UNIVERSE_COLUMN

            Box(
                modifier = Modifier.padding(
                    paddingValues = paddingValues.applyWithLinePosition(reachLineStart, reachLineEnd)
                )
            ) {
                content(startIndex)
            }
        }

        global.increaseSpan(span = span)
        global.increaseIndex()
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
        val startIndex = global.compositionIndex
        val startCurrentLineSpan = global.compositionCurrentLine

        gridScope.items(
            count = count,
            key = key,
            contentType = contentType,
            span = { GridItemSpan(span) }
        ) { offsetIndex ->
            val currentIndex = startIndex + offsetIndex
            val targetEndSpan = global.precalcEndSpan(startCurrentLineSpan, span, offsetIndex)
            val reachLineEnd = targetEndSpan == UNIVERSE_COLUMN
            val reachLineStart = targetEndSpan - span == 0

            Box(
                modifier = Modifier.padding(
                    paddingValues = paddingValues.applyWithLinePosition(reachLineStart, reachLineEnd)
                )
            ) {
                content(currentIndex)
            }
        }
        global.increaseSpan(span = span, count = count)
        global.increaseIndex(count = count)
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
        val startIndex = global.compositionIndex
        val startCurrentLineSpan = global.compositionCurrentLine

        gridScope.itemsIndexed(
            items = items,
            key = key,
            contentType = contentType,
            span = { index, item -> GridItemSpan(span) }
        ) { offsetIndex, item ->
            val currentIndex = startIndex + offsetIndex
            val targetEndSpan = global.precalcEndSpan(startCurrentLineSpan, span, offsetIndex)
            val reachLineEnd = targetEndSpan == UNIVERSE_COLUMN
            val reachLineStart = targetEndSpan - span == 0

            Box(
                modifier = Modifier.padding(
                    paddingValues = paddingValues.applyWithLinePosition(reachLineStart, reachLineEnd)
                )
            ) {
                content(currentIndex, item)
            }
        }
        global.increaseSpan(span = span, count = items.size)
        global.increaseIndex(count = items.size)
    }
}

@Composable
private fun PaddingValues.applyWithLinePosition(
    reachLineStart: Boolean,
    reachLineEnd: Boolean
): PaddingValues {
    return PaddingValues(
        start = if (reachLineStart) calculateStartPadding(layoutDirection = LocalLayoutDirection.current)
        else 0.dp,
        end = if (reachLineEnd) calculateEndPadding(layoutDirection = LocalLayoutDirection.current)
        else 0.dp
    )
}

@MultiLayoutDsl
interface MultiLayoutScope : MultiLayoutLazyScope {
    fun doComposite(layoutContent: context(LazyGridScope) MultiLayoutScope.() -> Unit): LazyGridScope.() -> Unit {
        return {
            global.resetIndex()
            global.resetSpan()
            layoutContent()
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
        content = scope.doComposite(layoutContent)
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
        contentPadding(contentPadding = PaddingValues(horizontal = 8.dp)) {
            span(12) {
                item { index ->
                    TestItem(
                        modifier = Modifier,
                        index = index
                    )
                }
            }

            gap(horizontalGap = 8.dp) {
                divider(span = 12)

                span(4) {
                    item { index ->
                        TestItem(
                            modifier = Modifier,
                            index = index
                        )
                    }
                }
            }

            items(count = 3, span = 6) { index ->
                TestItem(
                    modifier = Modifier,
                    index = index
                )
            }

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